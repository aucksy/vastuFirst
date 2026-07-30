#!/usr/bin/env python
"""Fail the build if an APK is signed by anything other than our stable test key.

WHY THIS EXISTS: for every release up to v0.3.16, the cloud build signed the APK with a key it
generated on the spot, because a fresh CI runner has no ~/.android/debug.keystore and AGP quietly
makes one. Android will not install an update whose signature changed, so each new build had to be
uninstalled first — and uninstalling erases the app's data. The owner lost every home he had drawn,
every single release, and nothing anywhere said why. The build was green each time.

That is the worst shape a defect can have: invisible to the compiler, invisible to the tests,
invisible to the screenshots, and destructive on a real phone several days later. So it gets a gate.

Pure standard library on purpose — this has to run on any runner without installing anything. It
parses the APK Signing Block (v2/v3) directly, because a modern APK has no META-INF certificate to
read: v1 JAR signing is skipped once minSdk is 24 or higher.

Usage: python scripts/check-apk-signature.py <apk> [expected-sha256-file]
"""
import glob
import hashlib
import struct
import sys

MAGIC = b"APK Sig Block 42"
SCHEME_V2 = 0x7109871A
SCHEME_V3 = 0xF05368C0
DEFAULT_EXPECTED = "signing/expected-cert-sha256.txt"


def u32(buf, off):
    return struct.unpack_from("<I", buf, off)[0]


def signing_block(data):
    """The id → value pairs of the APK Signing Block, which sits just before the central directory."""
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ValueError("not a zip: no end-of-central-directory record")
    cd_offset = u32(data, eocd + 16)
    if data[cd_offset - 16:cd_offset] != MAGIC:
        raise ValueError("no APK Signing Block — the APK is unsigned or v1-only")
    size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    body = data[cd_offset - size - 8 + 8:cd_offset - 24]

    pairs, pos = {}, 0
    while pos < len(body):
        length = struct.unpack_from("<Q", body, pos)[0]
        pairs[u32(body, pos + 8)] = body[pos + 12:pos + 8 + length]
        pos += 8 + length
    return pairs


def signer_certificate(block):
    """The first signer's certificate, DER encoded.

    Layout: signers → signer → signed_data → [digests][certificates], every level length-prefixed.
    """
    off = 4                                  # length of the signers sequence
    off += 4                                 # length of the first signer
    signed_data_len = u32(block, off)
    signed_data = block[off + 4:off + 4 + signed_data_len]

    pos = 0
    pos += 4 + u32(signed_data, pos)         # skip the content digests
    pos += 4                                 # length of the certificates sequence
    cert_len = u32(signed_data, pos)
    pos += 4
    return signed_data[pos:pos + cert_len]


def fingerprint(path):
    data = open(path, "rb").read()
    pairs = signing_block(data)
    block = pairs.get(SCHEME_V2) or pairs.get(SCHEME_V3)
    if block is None:
        raise ValueError("APK carries no v2 or v3 signature")
    schemes = "+".join(n for n, i in (("v2", SCHEME_V2), ("v3", SCHEME_V3)) if i in pairs)
    return hashlib.sha256(signer_certificate(block)).hexdigest(), schemes


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2

    apks = sorted(p for arg in sys.argv[1:2] for p in glob.glob(arg))
    if not apks:
        print("SIGNATURE CHECK FAILED: no APK matched %r" % sys.argv[1])
        return 1

    expected_file = sys.argv[2] if len(sys.argv) > 2 else DEFAULT_EXPECTED
    try:
        expected = open(expected_file).read().strip().lower()
    except OSError as exc:
        print("SIGNATURE CHECK FAILED: cannot read %s (%s)" % (expected_file, exc))
        return 1

    failed = False
    for apk in apks:
        try:
            got, schemes = fingerprint(apk)
        except Exception as exc:
            print("SIGNATURE CHECK FAILED: %s — %s" % (apk, exc))
            failed = True
            continue

        name = apk.rsplit("/", 1)[-1]
        if got == expected:
            print("  ok   %s signed by the stable test key (%s, %s…)" % (name, schemes, got[:16]))
        else:
            failed = True
            print("SIGNATURE CHECK FAILED: %s is signed by the WRONG key." % name)
            print("  expected %s" % expected)
            print("  got      %s" % got)
            print("")
            print("  Installing this build would force every tester to UNINSTALL first, which erases")
            print("  every home they have saved. Either the signing config in app/build.gradle.kts")
            print("  stopped pointing at signing/vastufirst-debug.keystore, or the key changed on")
            print("  purpose — and if it changed on purpose, update signing/expected-cert-sha256.txt")
            print("  in the same commit and say so in the release notes, because testers will need")
            print("  to reinstall exactly once.")

    if failed:
        return 1
    print("Signature check passed: the APK updates in place, so saved homes survive.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
