import { appendFile } from 'node:fs/promises';
import { createPrivateKey, sign } from 'node:crypto';
import { pathToFileURL } from 'node:url';

const appId = '6836798813';
const bundleId = 'com.localagentllm.apple';
const betaReady = new Set(['READY_FOR_BETA_TESTING', 'IN_BETA_TESTING']);
const editable = new Set(['PREPARE_FOR_SUBMISSION', 'DEVELOPER_REJECTED', 'REJECTED', 'METADATA_REJECTED', 'READY_FOR_REVIEW']);
const submittedStates = new Set(['WAITING_FOR_REVIEW', 'IN_REVIEW', 'PENDING_APPLE_RELEASE',
  'PENDING_DEVELOPER_RELEASE', 'PROCESSING_FOR_APP_STORE', 'READY_FOR_SALE', 'READY_FOR_DISTRIBUTION']);

function apiClient({ ASC_KEY_ID: keyId, ASC_ISSUER_ID: issuer, ASC_KEY: secret }) {
  if (!keyId || !issuer || !secret) throw new Error('La clé App Store Connect est incomplète dans GitHub Secrets.');
  const key = createPrivateKey(secret.includes('BEGIN PRIVATE KEY') ? secret : Buffer.from(secret.trim(), 'base64'));
  const now = Math.floor(Date.now() / 1000);
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const header = encode({ alg: 'ES256', kid: keyId, typ: 'JWT' });
  const claims = encode({ iss: issuer, iat: now, exp: now + 600, aud: 'appstoreconnect-v1' });
  const payload = `${header}.${claims}`;
  const signature = sign('sha256', Buffer.from(payload), { key, dsaEncoding: 'ieee-p1363' });
  const token = `${payload}.${signature.toString('base64url')}`;

  return async function request(path, method = 'GET', data) {
    const response = await fetch(`https://api.appstoreconnect.apple.com/${path.startsWith('v2/') ? path : `v1/${path}`}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: data === undefined ? undefined : JSON.stringify({ data }),
      signal: AbortSignal.timeout(25_000),
    });
    if (response.status === 204) return null;
    const result = await response.json();
    if (!response.ok) {
      const codes = (result.errors ?? []).map(item => item.code).filter(Boolean).join(', ');
      throw new Error(`App Store Connect a renvoyé HTTP ${response.status}${codes ? ` (${codes})` : ''}.`);
    }
    return result;
  };
}

// Fail closed: no App Review submission unless the current App Store territory
// configuration is France only. This does not change account settings or export compliance.
export async function assertFranceOnly(request) {
  const { data: availability } = await request(`apps/${appId}/appAvailabilityV2`);
  if (!availability?.id || availability.attributes?.availableInNewTerritories !== false) {
    throw new Error('Publication bloquée : désactive la disponibilité automatique dans les nouveaux territoires.');
  }
  const result = await request(`v2/appAvailabilities/${encodeURIComponent(availability.id)}/territoryAvailabilities?include=territory&limit=200`);
  if (result.links?.next || !Array.isArray(result.data) || !result.data.length ||
      result.data.some(item => typeof item.attributes?.available !== 'boolean' ||
        item.relationships?.territory?.data?.type !== 'territories' ||
        !item.relationships.territory.data.id)) {
    throw new Error('Publication bloquée : la liste complète des territoires Apple ne peut pas être vérifiée.');
  }
  const active = result.data.filter(item => item.attributes.available)
    .map(item => item.relationships.territory.data.id);
  if (active.length !== 1 || active[0] !== 'FRA') {
    throw new Error('Publication bloquée : sélectionne uniquement la France dans App Store Connect.');
  }
}

async function submitForReview(request, versionId) {
  const drafts = await request(`apps/${appId}/reviewSubmissions?filter%5Bstate%5D=READY_FOR_REVIEW&limit=50`);
  if (drafts.links?.next) throw new Error('Vérifie les brouillons App Review dans App Store Connect.');
  const itemsPath = id => `reviewSubmissions/${id}/items?include=appStoreVersion&limit=10`;
  const itemsByDraft = await Promise.all(drafts.data.filter(draft => !draft.attributes.platform || draft.attributes.platform === 'IOS')
    .map(async draft => ({ draft, items: (await request(itemsPath(draft.id))).data })));
  const includesVersion = item => item.relationships?.appStoreVersion?.data?.id === versionId;
  const existing = itemsByDraft.find(candidate => candidate.items.some(includesVersion));
  if (existing && existing.items.length !== 1) {
    throw new Error('Le brouillon App Review contient aussi d’autres éléments. Vérifie-le avant de soumettre.');
  }
  const submission = existing?.draft ?? itemsByDraft.find(candidate => candidate.items.length === 0)?.draft
    ?? (await request('reviewSubmissions', 'POST', {
      type: 'reviewSubmissions', relationships: { app: { data: { type: 'apps', id: appId } } },
    })).data;
  if (!existing) {
    await request('reviewSubmissionItems', 'POST', {
      type: 'reviewSubmissionItems', relationships: {
        reviewSubmission: { data: { type: 'reviewSubmissions', id: submission.id } },
        appStoreVersion: { data: { type: 'appStoreVersions', id: versionId } },
      },
    });
  }
  const path = `reviewSubmissions/${submission.id}`;
  const snapshot = await request(itemsPath(submission.id));
  if (snapshot.links?.next || snapshot.data.length !== 1 || !includesVersion(snapshot.data[0])) {
    throw new Error('Le brouillon App Review ne contient pas exactement la version demandée.');
  }
  await request(path, 'PATCH', { type: 'reviewSubmissions', id: submission.id, attributes: { submitted: true } });
  const status = (await request(path)).data.attributes;
  if (!status.submittedDate) throw new Error('La soumission a été envoyée. Vérifie son statut dans App Store Connect.');
  return status.state;
}

export async function prepareVersion(request, { appVersion, buildNumber, submit = false }) {
  if (!/^\d+\.\d+(?:\.\d+)?$/.test(appVersion ?? '') || !/^[1-9]\d*$/.test(buildNumber ?? '')) {
    throw new Error('Indique la version iOS et le numéro de build tels qu’ils apparaissent dans TestFlight.');
  }
  const app = (await request(`apps/${appId}`)).data;
  if (app.attributes.bundleId !== bundleId) throw new Error('L’application Apple ne correspond pas à Local Agent LLM.');

  const query = new URLSearchParams({ 'filter[app]': appId, 'filter[version]': buildNumber, include: 'preReleaseVersion' });
  const builds = await request(`builds?${query}`);
  const buildsForVersion = builds.data.filter(build => {
    const prereleaseId = build.relationships?.preReleaseVersion?.data?.id;
    const prerelease = builds.included?.find(item => item.id === prereleaseId);
    return build.attributes.version === buildNumber
      && prerelease?.attributes.platform === 'IOS' && prerelease.attributes.version === appVersion;
  });
  if (buildsForVersion.length !== 1) throw new Error(`Build iOS ${appVersion} (${buildNumber}) introuvable ou ambigu.`);
  const [build] = buildsForVersion;
  if (build.attributes.processingState !== 'VALID' || build.attributes.expired || build.attributes.buildAudienceType === 'INTERNAL_ONLY') {
    throw new Error('Ce build doit être valide, actif et éligible à l’App Store.');
  }
  const beta = (await request(`builds/${build.id}/buildBetaDetail`)).data.attributes;
  if (!betaReady.has(beta.internalBuildState)) {
    throw new Error(`Le build ${buildNumber} n’est pas disponible pour le test interne (${beta.internalBuildState}).`);
  }

  if (submit) await assertFranceOnly(request);

  const versionsResponse = await request(`apps/${appId}/appStoreVersions?filter%5Bplatform%5D=IOS&limit=200`);
  if (versionsResponse.links?.next) throw new Error('La liste des versions Apple doit être vérifiée avant de préparer un brouillon.');
  const versions = versionsResponse.data.filter(entry => entry.attributes.platform === 'IOS');
  let version = versions.find(entry => entry.attributes.versionString === appVersion);
  const versionState = entry => entry.attributes.appVersionState ?? entry.attributes.appStoreState;
  const releaseType = submit ? 'AFTER_APPROVAL' : 'MANUAL';
  const firstDraft = !version && versions.length === 1 && versionState(versions[0]) === 'PREPARE_FOR_SUBMISSION';
  if (firstDraft) {
    const relation = await request(`appStoreVersions/${versions[0].id}/relationships/build`);
    if (!relation.data) version = versions[0];
  }

  if (version) {
    const state = versionState(version);
    if (submit && submittedStates.has(state)) {
      const linked = await request(`appStoreVersions/${version.id}/relationships/build`);
      if (linked.data?.id === build.id) return `Build ${buildNumber} déjà soumis à App Review (${state}).`;
    }
    if (!editable.has(state)) throw new Error(`Cette version de l’App Store est verrouillée (${state}).`);
    const versionChange = version.attributes.versionString !== appVersion ? { versionString: appVersion } : {};
    if (versionChange.versionString || version.attributes.releaseType !== releaseType) {
      await request(`appStoreVersions/${version.id}`, 'PATCH', {
        type: 'appStoreVersions', id: version.id, attributes: { ...versionChange, releaseType },
      });
    }
  } else {
    if (versions.some(entry => editable.has(versionState(entry)))) {
      throw new Error('Un autre brouillon iOS existe déjà dans App Store Connect. Vérifie-le avant de créer une version.');
    }
    version = (await request('appStoreVersions', 'POST', {
      type: 'appStoreVersions', attributes: { platform: 'IOS', versionString: appVersion, releaseType },
      relationships: { app: { data: { type: 'apps', id: appId } } },
    })).data;
  }

  const versionPath = `appStoreVersions/${version.id}`;
  const relationshipPath = `${versionPath}/relationships/build`;
  if ((await request(relationshipPath)).data?.id !== build.id) {
    await request(relationshipPath, 'PATCH', { type: 'builds', id: build.id });
  }
  const [saved, linked] = await Promise.all([request(versionPath), request(relationshipPath)]);
  const attributes = saved.data.attributes;
  if (attributes.versionString !== appVersion || attributes.releaseType !== releaseType || linked.data?.id !== build.id) {
    throw new Error('Apple n’a pas confirmé le brouillon, son build et son mode de publication.');
  }
  if (submit) {
    await assertFranceOnly(request);
    const status = await submitForReview(request, version.id);
    return `App Review a reçu Local Agent LLM ${appVersion} (${buildNumber}). Statut : ${status}. Publication après approbation Apple.`;
  }
  return `Brouillon App Store iOS ${appVersion} préparé avec le build ${buildNumber}. Publication manuelle après App Review.`;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const message = await prepareVersion(apiClient(process.env), {
      appVersion: process.env.APP_VERSION, buildNumber: process.env.BUILD_NUMBER,
      submit: process.env.SUBMIT_FOR_REVIEW === 'true',
    });
    if (process.env.GITHUB_STEP_SUMMARY) await appendFile(process.env.GITHUB_STEP_SUMMARY, `${message}\n`);
    console.log(message);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
