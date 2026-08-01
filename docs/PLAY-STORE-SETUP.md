# Putting VastuFirst on the Play Store — what you do, and what I do

Plain English. Everything in **YOU** needs your Google account and cannot be done for you.
Everything in **ME** is already built and waiting.

---

## 1. The account — YOU · about ₹2,100, one time

Go to **play.google.com/console** and sign up as a developer with the Google account you want to own
this app **forever**. Google charges a one-time **$25** (about ₹2,100). Choose a **personal** account
unless you have a registered company — a company account asks for a DUNS number and takes weeks.

Google will ask you to verify your identity with an ID document. Do this early; it can take a few
days and nothing can be published until it clears.

**Then tell me the email address on the account.** That is all I need from this step.

---

## 2. The signing key — YOU, and this one really matters

Google needs a private signature that proves every future update comes from you. **If it is ever
lost, you can never update the app again** — you would have to publish a brand-new listing and every
customer would have to reinstall.

When you create the app in Play Console, choose **"Use Google Play app signing"**. It is the default.
Google keeps the master key in its own vault, and you cannot lose it.

**Send me nothing yet.** I have already set the build up so it produces the file you upload.

### ⭐ Where the key goes when you do have it — GitHub secrets, four of them

At `github.com/aucksy/vastuFirst` → **Settings → Secrets and variables → Actions → New repository
secret**. Exactly these four names, because that is what the build reads:

| Secret name | What to paste |
|---|---|
| `RELEASE_STORE_FILE_BASE64` | the keystore file, base64-encoded (I will give you the one command) |
| `RELEASE_STORE_PASSWORD` | the password you set when creating it |
| `RELEASE_KEY_ALIAS` | the alias you set when creating it |
| `RELEASE_KEY_PASSWORD` | the key password (usually the same one) |

⚠ **The keystore is a binary file, and a GitHub secret only holds text** — so it goes in base64 and
the build decodes it. **That decode step is now built and live.** It is deliberately conditional: if
those four secrets are not set, the release builds and publishes exactly as it does today, signed
with the committed test key. The moment you add them, the very next tag is signed with your real key
instead — nothing else needs changing, and nobody has to remember to flip anything.

It also checks the key opens before the build depends on it, because a half-pasted secret otherwise
fails much later with a message that says nothing useful. The decoded file is written outside the
project, never into the repository, and deleted when the job ends whether it succeeded or not.

⚠ **NOTHING GOES IN THE APP ITSELF, ever.** These four only ever exist on GitHub. An APK is a zip
anybody can open, so a signing key inside one would be worthless.

### And the payment key? There isn't one.

Google Play Billing recognises the app by its own signature and package name. There is **no key, no
token and no secret to paste anywhere** for the ₹699 checkout. It goes live by creating the item in
Play Console and flipping one build setting.

---

## 3. The ₹699 item — YOU, five clicks, once the account exists

In Play Console: **Monetise → Products → In-app products → Create product**.

| Field | What to put |
|---|---|
| Product ID | `vastufirst_full_report` — **exactly this**, it is what the app asks for |
| Name | Full Vastu report |
| Description | Every issue ranked, with the layout change and remedy for each. |
| Price | **₹699** |

Set it to **Active**. Then tell me it exists and I will switch payments on in the next build.

⚠ **Google keeps 15%** of each sale (about ₹105 of ₹699) for the first $1M a year. That is not
optional for an app sold on the Play Store — see the note at the bottom.

---

## 4. The store listing — YOU approve, I draft

I will write all of these for you to change or approve. What I need from you:

- ~~The email address customers should write to.~~ **DECIDED: `contact@vastufirst.com`.** Already in
  the app's privacy screen and in the crash-report email. ⚠ It must be RECEIVING mail before the
  store listing goes live — Google shows it publicly and a bouncing address fails review.
- **Whether the developer name shown to customers** should be your own name or a business name.

I will supply: the short description, the full description, the screenshots (from the app's own
rendered screens), the feature graphic, and the content-rating answers.

---

## 5. Privacy policy — done

Google requires a privacy policy at a public web address. The policy is written and lives in the app
under **Settings → Privacy**, and the same words are in `docs/PRIVACY-POLICY.md`.

**What I need from you:** somewhere to put it publicly. The free option is a GitHub Pages page, which
I can set up in ten minutes and costs nothing. Say the word.

---

## 6. Data safety form — I have the answers ready

Google asks a long form about what data the app collects. For VastuFirst the honest answers are
almost all "no", which makes this quick:

| Question | Answer |
|---|---|
| Does your app collect or share user data? | **No** |
| Is data encrypted in transit? | Yes — the one upload uses HTTPS |
| Can users request data deletion? | Yes — Settings → Delete all my data, on the device |

The one thing to declare is **Photos**: when a user chooses to have a plan read, that picture is sent
to be processed and not stored. It is declared as *processed ephemerally, not collected*.

---

## ⚠ The one thing I changed without asking, and why

**The plan said Razorpay. Google does not allow it here.**

Google's rules: anything sold *inside* a Play Store app that the customer then uses *inside* the app
must be sold through Google's own payment system. A Vastu report read in the app is exactly that.
Using Razorpay for it gets the app removed from the store.

So the checkout is built on Google Play billing instead. It is complete and switched off; it goes
live when you have done step 3 and I flip one setting. **There is nothing secret for you to send me** —
Google recognises the app by its own signature.

**Razorpay is still the right answer if you ever sell the report on a web page instead of in the app.**
Then you keep the full ₹699 and Google takes nothing — but the customer has to pay on a website first,
which is a worse experience and a lot more to build. Your call, and it can wait.

---

## What happens if you do nothing

The app keeps working exactly as it does now. The unlock screen says, in plain words, that no payment
is taken and the report unlocks on the device. Nothing anywhere pretends to charge.
