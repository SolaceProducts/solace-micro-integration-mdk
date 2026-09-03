#!/bin/bash
# download-binder-source.sh  (Linux / macOS)
#
# Downloads a third-party Spring Cloud Stream binder source from GitHub
# into a local directory for offline analysis.
#
# Handles two cases:
#   1. Standalone repo  — shallow clone (only top commit, no history)
#   2. Monorepo subtree — git sparse-checkout (fetches ONLY the subtree blobs,
#      NOT the entire repository)
#
# Usage:
#   bash download-binder-source.sh <github_url> <output_dir>
#
# Arguments:
#   github_url  GitHub URL — repo root or /tree/branch/path for subtrees
#   output_dir  Local directory to receive the source (cleaned before use)
#
# Examples:
#   # Standalone repo
#   bash download-binder-source.sh \
#     https://github.com/solace-spring-cloud/spring-cloud-stream-binder-solace \
#     ./.TEMP
#
#   # Monorepo subtree (downloads ONLY the kafka-binder subtree)
#   bash download-binder-source.sh \
#     https://github.com/spring-cloud/spring-cloud-stream/tree/main/binders/kafka-binder \
#     ./.TEMP
#
# What gets downloaded for monorepo subtrees:
#   - The binder subtree itself (all modules under that path)
#   - The root pom.xml (needed for version/property resolution)
#   - Nothing else — the rest of the monorepo is NOT fetched
#
# After completion the script prints BINDER_SOURCE_ROOT=<path> which is the
# directory containing the binder's top-level pom.xml.

set -euo pipefail

# ── Argument validation ──────────────────────────────────────────────
GITHUB_URL="${1:?ERROR: missing argument. Usage: $0 <github_url> <output_dir>}"
OUTPUT_DIR="${2:?ERROR: missing argument. Usage: $0 <github_url> <output_dir>}"

# ── Parse GitHub URL ─────────────────────────────────────────────────
# Supported formats:
#   https://github.com/owner/repo
#   https://github.com/owner/repo/tree/branch
#   https://github.com/owner/repo/tree/branch/path/to/subtree
parse_github_url() {
    local url="$1"
    url="${url%/}"                      # strip trailing slash
    url="${url%.git}"                   # strip .git suffix if present

    if [[ "$url" =~ ^https://github\.com/([^/]+)/([^/]+)(/tree/([^/]+)(/(.+))?)?$ ]]; then
        GH_OWNER="${BASH_REMATCH[1]}"
        GH_REPO="${BASH_REMATCH[2]}"
        GH_BRANCH="${BASH_REMATCH[4]:-main}"
        GH_SUBTREE="${BASH_REMATCH[6]:-}"
    else
        echo "ERROR: cannot parse GitHub URL: $url" >&2
        echo "Expected format: https://github.com/owner/repo[/tree/branch[/path]]" >&2
        exit 1
    fi
}

parse_github_url "$GITHUB_URL"

echo "=========================================="
echo " Binder Source Download"
echo "=========================================="
echo " Repository : ${GH_OWNER}/${GH_REPO}"
echo " Branch     : ${GH_BRANCH}"
echo " Subtree    : ${GH_SUBTREE:-<entire repo>}"
echo " Output dir : ${OUTPUT_DIR}"
echo "=========================================="
echo ""

# ── Prepare output directory ─────────────────────────────────────────
if [ -d "$OUTPUT_DIR" ]; then
    echo "[1/4] Cleaning existing output directory..."
    rm -rf "$OUTPUT_DIR"
fi
mkdir -p "$OUTPUT_DIR"

CLONE_DIR="$OUTPUT_DIR/repo"

# ── Download ─────────────────────────────────────────────────────────

if [ -z "$GH_SUBTREE" ]; then
    # ── Strategy A: standalone repo → shallow clone ──────────────────
    echo "[2/4] Shallow-cloning standalone repository..."
    git clone --depth 1 --branch "$GH_BRANCH" \
        "https://github.com/${GH_OWNER}/${GH_REPO}.git" \
        "$CLONE_DIR" 2>&1

    BINDER_SOURCE_ROOT="$CLONE_DIR"
else
    # ── Strategy B: monorepo subtree → sparse checkout ───────────────
    echo "[2/4] Sparse-cloning monorepo (metadata only, no file content yet)..."
    git clone --depth 1 \
              --filter=blob:none \
              --sparse \
              --branch "$GH_BRANCH" \
              "https://github.com/${GH_OWNER}/${GH_REPO}.git" \
              "$CLONE_DIR" 2>&1

    echo ""
    echo "[3/4] Fetching ONLY the binder subtree..."
    (
        cd "$CLONE_DIR"
        # Cone mode only accepts directories — set the binder subtree
        git sparse-checkout set "$GH_SUBTREE" 2>&1

        # Fetch root pom.xml individually (needed for version/property resolution)
        # git-show works even when the file is outside the sparse-checkout cone
        echo ""
        echo "      Extracting root pom.xml for version resolution..."
        git show "HEAD:pom.xml" > pom.xml 2>/dev/null \
            && echo "      root pom.xml: OK" \
            || echo "      root pom.xml: not found (non-fatal)"
    )

    BINDER_SOURCE_ROOT="$CLONE_DIR/$GH_SUBTREE"
fi

# ── Remove .git to save space ────────────────────────────────────────
echo ""
echo "[4/4] Removing .git metadata to save disk space..."
rm -rf "$CLONE_DIR/.git"

# ── Summary ──────────────────────────────────────────────────────────
echo ""
echo "=========================================="
echo " Download complete"
echo "=========================================="

JAVA_COUNT=$(find "$CLONE_DIR" -name '*.java' -type f 2>/dev/null | wc -l)
XML_COUNT=$(find "$CLONE_DIR" -name '*.xml' -type f 2>/dev/null | wc -l)
TOTAL_COUNT=$(find "$CLONE_DIR" -type f 2>/dev/null | wc -l)
echo " Java files : ${JAVA_COUNT}"
echo " XML files  : ${XML_COUNT}"
echo " Total files: ${TOTAL_COUNT}"

echo ""
echo " BINDER_SOURCE_ROOT=${BINDER_SOURCE_ROOT}"
echo "=========================================="
