#!/usr/bin/env bash
# Manifest guard.
#
# WHY THIS EXISTS: "scan your plan" was written, reviewed, tested and nearly tagged before anybody
# noticed the app had no INTERNET permission. Nothing would have caught it — it compiles, every unit
# test passes, the render goldens are identical, and the failure on a real phone is a polite
# "we couldn't read your plan just now" on every single attempt, for every user, forever. Exactly the
# class of defect docs/UI-POLISH.md exists for: invisible to the build, obvious on the device.
#
# It also guards the other direction. An app that scores homes offline has no business collecting
# location, contacts, the camera roll or the microphone, and a permission is the kind of thing a
# library can add to a merged manifest without anyone deciding to.
set -euo pipefail

MANIFEST="app/src/androidMain/AndroidManifest.xml"
fail=0

if [ ! -f "$MANIFEST" ]; then
  echo "MISSING: $MANIFEST"
  exit 1
fi

# Required: the plan reader is one HTTPS POST and cannot work without this.
if ! grep -q 'android.permission.INTERNET' "$MANIFEST"; then
  echo "MANIFEST VIOLATION: android.permission.INTERNET is missing."
  echo "  Scanning a plan makes an HTTPS request. Without it every read fails with a"
  echo "  SecurityException and the app reports 'we couldn't read your plan just now' forever."
  fail=1
fi

# Forbidden: nothing in this product needs any of these, and each one costs user trust in a feature
# whose whole problem is asking someone for a picture of their home.
for perm in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION CAMERA RECORD_AUDIO READ_CONTACTS \
            READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE READ_PHONE_STATE; do
  if grep -q "android.permission.$perm" "$MANIFEST"; then
    echo "MANIFEST VIOLATION: android.permission.$perm is declared and nothing here needs it."
    echo "  The system photo picker returns a chosen file without any storage permission, and"
    echo "  'take a photo' hands the job to the phone's own camera app — which needs the CAMERA"
    echo "  grant only from an app that DECLARES it. Declaring it here would create the very"
    echo "  permission prompt this app has never had to show."
    fail=1
  fi
done

if [ "$fail" -ne 0 ]; then
  echo ""
  echo "Manifest check FAILED."
  exit 1
fi
echo "Manifest check passed: INTERNET present, no permission we don't need."
