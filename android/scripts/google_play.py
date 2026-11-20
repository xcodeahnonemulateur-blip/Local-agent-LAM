"""Publish a signed bundle and the reviewed store assets in one Google Play edit.

Credentials are supplied only by GitHub Actions; they are never written or logged.
"""
import argparse
import json
import os
from pathlib import Path
from urllib.parse import quote

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "com.nathandev.localagent"
ROOT = Path(__file__).resolve().parents[1]
BASE = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["version", "publish"])
    parser.add_argument("--code", type=int)
    parser.add_argument("--version")
    args = parser.parse_args()
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_SERVICE_ACCOUNT"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = AuthorizedSession(credentials)

    def request(method, url, **kwargs):
        response = session.request(method, url, timeout=300, **kwargs)
        if not response.ok:
            # Only the public API error is logged, never credentials or request headers.
            try:
                message = response.json()["error"]["message"]
            except (ValueError, KeyError):
                message = "Google Play request failed"
            raise RuntimeError(f"Google Play HTTP {response.status_code}: {message}")
        return response.json() if response.content else {}

    edit = request("POST", BASE + "/edits", json={})["id"]
    endpoint = BASE + "/edits/" + edit
    committed = False
    try:
        if args.action == "version":
            # Also advance past discarded uploads from an earlier workflow attempt.
            codes = [301, 298 + int(os.environ.get("GITHUB_RUN_NUMBER", "3"))]
            for bundle in request("GET", endpoint + "/bundles").get("bundles", []):
                codes.append(int(bundle["versionCode"]))
            for track in request("GET", endpoint + "/tracks").get("tracks", []):
                for release in track.get("releases", []):
                    codes.extend(map(int, release.get("versionCodes", [])))
            code = max(codes) + 1
            version = f"0.4.{code - 302}"
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(f"code={code}\nversion={version}\n")
            print(f"Next Google Play version: {version} ({code})")
            return

        if not args.code or not args.version:
            raise ValueError("A verified version and code are required")
        bundle_path = ROOT / "app/build/outputs/bundle/release/app-release.aab"
        with bundle_path.open("rb") as bundle:
            uploaded = request("POST", UPLOAD + f"/edits/{edit}/bundles?uploadType=media",
                               data=bundle, headers={"Content-Type": "application/octet-stream"})
        if int(uploaded["versionCode"]) != args.code:
            raise RuntimeError("Uploaded bundle version does not match the build")

        request("PUT", endpoint + "/tracks/production", json={
            "track": "production", "releases": [{
                "name": args.version, "versionCodes": [str(args.code)], "status": "completed",
                "releaseNotes": [
                    {"language": "en-US", "text": "Refreshed phone interface, compact agent menu, light and dark themes, new LAM icon, improved message sending and secure square QR pairing with Desktop."},
                    {"language": "fr-FR", "text": "Interface adaptée au téléphone, menu des agents compact, thèmes clair et sombre, nouvelle icône LAM, envoi des messages corrigé et jumelage sécurisé au PC par QR carré."},
                ],
            }],
        })
        screenshots = sorted((ROOT / "store/screenshots").glob("*.png"))
        if len(screenshots) < 2:
            raise RuntimeError("At least two verified phone screenshots are required")
        listings = request("GET", endpoint + "/listings").get("listings", [])
        if not listings:
            raise RuntimeError("The existing Google Play listing is missing")
        for listing in listings:
            language = listing["language"]
            path = endpoint + "/listings/" + quote(language, safe="")
            body = {key: listing[key] for key in ("language", "shortDescription", "fullDescription", "video") if key in listing}
            body["title"] = "Local Agent LLM"
            request("PUT", path, json=body)
            for image_type, images in (("icon", [ROOT / "store/icon-512.png"]), ("phoneScreenshots", screenshots)):
                request("DELETE", path + "/" + image_type)
                for image in images:
                    with image.open("rb") as content:
                        request("POST", UPLOAD + f"/edits/{edit}/listings/{quote(language, safe='')}/{image_type}?uploadType=media",
                                data=content, headers={"Content-Type": "image/png"})
            print(f"Updated title, LAM icon and {len(screenshots)} screenshots: {language}")
        # Google's commit endpoint validates the whole edit before applying it:
        # https://developers.google.com/android-publisher/edits/
        # Use the same commit flow as the previously successful upload action.
        request("POST", endpoint + ":commit")
        committed = True
        summary = f"Submitted Local Agent LLM {args.version} ({args.code}) to Google Play production, including the new icon and phone screenshots. Google review may still be pending."
        print(summary)
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as output:
                output.write(summary + "\n")
    finally:
        if not committed:
            session.delete(endpoint, timeout=30)
        session.close()


if __name__ == "__main__":
    main()
