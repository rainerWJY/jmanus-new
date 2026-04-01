#!/bin/bash

# Simple UI build script (serves under /lynxe)
# 1) Remove static/lynxe directory with git rm -r
# 2) Build frontend (outDir lynxe) and copy to static/lynxe
# 3) Add new lynxe directory to git

set -e  # Exit immediately on error

# Get the absolute path of the script directory, then project root = three levels up
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

echo "Starting simple UI build process (output under /lynxe)..."

# Step 1: Remove static/lynxe directory with git rm -r
echo "Step 1: Removing static/lynxe directory with git rm -r..."
if [ -d "$PROJECT_ROOT/src/main/resources/static/lynxe" ]; then
    git rm -r "$PROJECT_ROOT/src/main/resources/static/lynxe"
    echo "✓ static/lynxe directory removed"
else
    echo "ℹ static/lynxe directory does not exist, skipping removal"
fi

# Step 2: Build frontend and copy to static directory
echo "Step 2: Building frontend and copying to static directory..."

# Enter ui-vue3 directory and build
cd "$PROJECT_ROOT/ui-vue3"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    pnpm install
fi

# Build the project (Vite outDir is ./lynxe, base is /lynxe)
echo "Building frontend..."
pnpm build

# Create static directory if it doesn't exist
mkdir -p "$PROJECT_ROOT/src/main/resources/static"

# Copy built lynxe directory to static
echo "Copying lynxe directory to static..."
cp -r "$PROJECT_ROOT/ui-vue3/lynxe" "$PROJECT_ROOT/src/main/resources/static/"

echo "✓ UI files copied to static/lynxe"

# Step 3: Add new lynxe directory to git
echo "Step 3: Adding new lynxe directory to git..."
cd "$PROJECT_ROOT"
git add src/main/resources/static/lynxe

echo "✓ New lynxe directory added to git"

echo ""
echo "=== Build Completed ==="
echo "Frontend files successfully deployed to: $PROJECT_ROOT/src/main/resources/static/lynxe/"
echo "UI is served at: http://localhost:18080/lynxe"
echo "You can now commit the changes with: git commit -m 'Update UI'"
