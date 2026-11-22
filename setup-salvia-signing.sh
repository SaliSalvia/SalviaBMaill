#!/data/data/com.termux/files/usr/bin/bash
#=====================================================================
#  SalviaBMail - Release Signing Setup (Termux)
#
#  Run this AFTER the repo has been pushed. It asks everything
#  interactively, generates a new keystore (default: SalviaBMail.jks)
#  and stores it in GitHub Actions secrets, so release builds are
#  signed with YOUR personal key.
#
#  Requirements:
#      pkg install gh git openjdk-17 coreutils
#      gh auth login      (already done)
#
#  Usage (from anywhere inside the repo):
#      bash setup-salvia-signing.sh
#=====================================================================

set -euo pipefail

#--------------- configuration (edit if needed) ----------------------
DEFAULT_KS="SalviaBMail.jks"        # keystore file name
DEFAULT_ALIAS="salviabmail"         # key alias
DEFAULT_VALIDITY=10000              # days (~27 years)
REPO_NAME="SalviaBMail"             # fallback repo name

# GitHub secret names -- change these 4 if your workflow expects
# different names (e.g. KEYSTORE_BASE64, STORE_PASSWORD, ...)
SEC_KEYSTORE="SIGNING_KEYSTORE"     # base64-encoded keystore file
SEC_STOREPASS="KEYSTORE_PASSWORD"   # keystore (store) password
SEC_ALIAS="KEY_ALIAS"               # key alias
SEC_KEYPASS="KEY_PASSWORD"          # key password
#---------------------------------------------------------------------

B='\033[1;34m'; G='\033[0;32m'; Y='\033[1;33m'; R='\033[0;31m'; N='\033[0m'
say()  { printf "${B}::${N} %s\n" "$*"; }
ok()   { printf "${G}[OK]${N} %s\n" "$*"; }
warn() { printf "${Y}[!]${N} %s\n" "$*"; }
die()  { printf "${R}[X]${N} %s\n" "$*" >&2; exit 1; }

echo
echo "====================================================="
echo "   SalviaBMail - Release Signing Setup (Termux)"
echo "====================================================="
echo

#------------------------- 1. checks ---------------------------------
say "Checking requirements..."
command -v gh      >/dev/null 2>&1 || die "gh missing      -> pkg install gh"
command -v git     >/dev/null 2>&1 || die "git missing     -> pkg install git"
command -v keytool >/dev/null 2>&1 || die "keytool missing -> pkg install openjdk-17"
command -v base64  >/dev/null 2>&1 || die "base64 missing  -> pkg install coreutils"

gh auth status >/dev/null 2>&1 \
  || die "gh is not logged in -> run: gh auth login"

GH_USER="$(gh api user -q .login)" \
  || die "Could not read the GitHub user (token scopes repo + read:org needed)"
ok "GitHub CLI authenticated as: ${GH_USER}"

#---------------------- 2. target repo -------------------------------
say "Detecting the target repository..."
REPO=""
REMOTE="$(git remote get-url origin 2>/dev/null || true)"
case "$REMOTE" in
  *github.com*)
    REMOTE="${REMOTE#*github.com[:/]}"
    REMOTE="${REMOTE%.git}"
    REMOTE="${REMOTE%/}"
    REPO="$REMOTE"
    ;;
esac
if [ -z "$REPO" ]; then
  REPO="${GH_USER}/${REPO_NAME}"
fi
read -r -p "Repo (owner/name) [${REPO}]: " ANS
if [ -n "$ANS" ]; then REPO="$ANS"; fi

gh repo view "$REPO" --json nameWithOwner -q .nameWithOwner >/dev/null 2>&1 \
  || die "Repo '${REPO}' not found or no access."
ok "Target repo: ${REPO}"

#------------------- 3. keystore questions ---------------------------
echo
say "Keystore settings (Enter = accept the [default])"

KS_FILE="$DEFAULT_KS"
read -r -p "  Keystore file name [${KS_FILE}]: " ANS
if [ -n "$ANS" ]; then KS_FILE="$ANS"; fi

if [ -e "$KS_FILE" ]; then
  warn "'${KS_FILE}' already exists in this folder."
  read -r -p "  Overwrite it with a NEW key? [y/N]: " OW
  OW="${OW:-n}"
  case "${OW,,}" in
    y|yes) rm -f "$KS_FILE" ;;
    *)     die "Existing keystore kept - aborting (nothing changed)." ;;
  esac
fi

echo
say "Passwords (typing is hidden)"

while true; do
  read -r -s -p "  Keystore password (min 6 chars): " STORE_PASS; echo
  if [ "${#STORE_PASS}" -lt 6 ]; then warn "Too short - use at least 6 characters."; continue; fi
  read -r -s -p "  Confirm keystore password: " P2; echo
  if [ "$STORE_PASS" = "$P2" ]; then break; fi
  warn "Passwords do not match - try again."
done

read -r -p "  Use the same password for the key? [Y/n]: " SAME
SAME="${SAME:-y}"
if [ "${SAME,,}" = "y" ]; then
  KEY_PASS="$STORE_PASS"
else
  while true; do
    read -r -s -p "  Key password (min 6 chars): " KEY_PASS; echo
    if [ "${#KEY_PASS}" -lt 6 ]; then warn "Too short - use at least 6 characters."; continue; fi
    read -r -s -p "  Confirm key password: " P2; echo
    if [ "$KEY_PASS" = "$P2" ]; then break; fi
    warn "Passwords do not match - try again."
  done
fi

echo
read -r -p "  Key alias [${DEFAULT_ALIAS}]: " ANS
ALIAS="${ANS:-$DEFAULT_ALIAS}"

read -r -p "  Validity in days [${DEFAULT_VALIDITY}]: " ANS
VALIDITY="${ANS:-$DEFAULT_VALIDITY}"

echo
say "Certificate identity (Distinguished Name)"
CN=""
while [ -z "$CN" ]; do
  read -r -p "  Your name / CN (required, e.g. Salvia Developer): " CN
done
read -r -p "  Organizational unit / OU (optional): " DN_OU
read -r -p "  Organization / O (optional): " DN_O
read -r -p "  City / L (optional): " DN_L
read -r -p "  State or province / ST (optional): " DN_ST
read -r -p "  Country code / C (optional, 2 letters, e.g. IR): " DN_C

DNAME="CN=${CN}"
if [ -n "$DN_OU" ]; then DNAME="${DNAME}, OU=${DN_OU}";  fi
if [ -n "$DN_O"  ]; then DNAME="${DNAME}, O=${DN_O}";   fi
if [ -n "$DN_L"  ]; then DNAME="${DNAME}, L=${DN_L}";   fi
if [ -n "$DN_ST" ]; then DNAME="${DNAME}, ST=${DN_ST}"; fi
if [ -n "$DN_C"  ]; then DNAME="${DNAME}, C=${DN_C}";   fi

# PKCS12 ignores a different key password on many JDK builds -> JKS then
if [ "$KEY_PASS" = "$STORE_PASS" ]; then
  STORE_TYPE="PKCS12"
else
  STORE_TYPE="JKS"
  warn "Different store/key passwords -> using JKS keystore type."
fi

#------------------- 4. generate keystore ----------------------------
echo
say "Generating keystore: ${KS_FILE} (RSA 2048 / ${VALIDITY} days / ${STORE_TYPE})"
keytool -genkeypair -v \
    -keystore "$KS_FILE" \
    -storetype "$STORE_TYPE" \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity "$VALIDITY" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -dname "$DNAME" \
  || die "keytool failed - keystore was NOT created."

keytool -list -keystore "$KS_FILE" -storetype "$STORE_TYPE" \
    -storepass "$STORE_PASS" >/dev/null 2>&1 \
  || die "Keystore verification failed."

ok "Keystore created and verified."

echo
say "Certificate fingerprints (useful for Firebase / API consoles):"
keytool -list -v -keystore "$KS_FILE" -storetype "$STORE_TYPE" \
    -storepass "$STORE_PASS" 2>/dev/null \
  | grep -E "SHA1:|SHA256:" | sed 's/^/    /' || true

#------------------- 5. gitignore safety -----------------------------
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  if ! git check-ignore -q "$KS_FILE" 2>/dev/null; then
    warn "'${KS_FILE}' is inside a git repo and is NOT ignored."
    read -r -p "  Add it to .gitignore now? [Y/n]: " GI
    GI="${GI:-y}"
    if [ "${GI,,}" = "y" ]; then
      printf '\n# release signing keystore - never commit this\n%s\n' "$KS_FILE" >> .gitignore
      ok "Added '${KS_FILE}' to .gitignore."
    else
      warn "Not ignored - be careful NOT to commit the keystore!"
    fi
  fi
fi

#------------------- 6. set GitHub secrets ---------------------------
echo
say "Uploading secrets to ${REPO} ..."
base64 -w 0 "$KS_FILE" | gh secret set "$SEC_KEYSTORE" -R "$REPO" \
  || die "Failed to set ${SEC_KEYSTORE} (token needs 'repo' scope)."
printf '%s' "$STORE_PASS" | gh secret set "$SEC_STOREPASS" -R "$REPO" \
  || die "Failed to set ${SEC_STOREPASS}."
printf '%s' "$KEY_PASS"   | gh secret set "$SEC_KEYPASS"  -R "$REPO" \
  || die "Failed to set ${SEC_KEYPASS}."
printf '%s' "$ALIAS"      | gh secret set "$SEC_ALIAS"    -R "$REPO" \
  || die "Failed to set ${SEC_ALIAS}."

ok "Secrets configured. Current secrets on ${REPO}:"
gh secret list -R "$REPO" | sed 's/^/    /' || true

#------------------- 7. done -----------------------------------------
echo
ok "All done! Release builds can now be signed with YOUR key."
echo
warn "BACKUP IS CRITICAL: if you lose '${KS_FILE}' or the passwords,"
warn "you can never update an app that was published with this key."
say "Copy the keystore to phone storage:"
echo "    termux-setup-storage"
echo "    cp ${KS_FILE} ~/storage/shared/"
echo
say "Then trigger your release workflow, e.g.:"
echo "    gh workflow list -R ${REPO}"
echo "    gh workflow run <workflow-name> -R ${REPO}   # if it has manual trigger"
echo "    # or push a tag:  git tag v1.0 && git push --tags"
echo
say "Note: if your workflow expects different secret names, edit the"
say "SEC_* variables at the top of this script and run it again."
