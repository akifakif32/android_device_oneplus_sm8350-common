#!/bin/bash

# =========================================================
# CONFIGURATION & PATHS
# =========================================================
PATCH_DIR="device/oneplus/sm8350-common/patches"
GAMESPACE_DIR="packages/apps/GameSpace"
VENDOR_GMS_DIR="vendor/gms"

# Clone Targets
PIXELWORKS_REPO="https://github.com/LineageOS/android_hardware_pixelworks_interfaces.git"
PIXELWORKS_DIR="hardware/pixelworks/interfaces"
PIXELWORKS_BRANCH="lineage-23.2"

# Updated to your specific repo and path
PRIV_KEYS_REPO="https://github.com/Jammy555/vendor_evolution-priv_keys-template.git"
PRIV_KEYS_DIR="vendor/lineage-priv/keys"

# =========================================================
# 1. CLONE REQUIRED REPOSITORIES
# =========================================================

echo "====================================="
echo "Cloning Required Repositories..."
echo "====================================="

# Clone Pixelworks
if [ ! -d "$PIXELWORKS_DIR" ]; then
    echo "Cloning Pixelworks interfaces..."
    git clone -b "$PIXELWORKS_BRANCH" "$PIXELWORKS_REPO" "$PIXELWORKS_DIR"
    if [ $? -eq 0 ]; then
        echo "✅ Pixelworks cloned successfully."
    else
        echo "❌ Failed to clone Pixelworks."
    fi
else
    echo "✅ Pixelworks directory already exists. Skipping clone."
fi

# Clone Private Keys
if [ ! -d "$PRIV_KEYS_DIR" ]; then
    echo "Cloning private keys..."
    git clone "$PRIV_KEYS_REPO" "$PRIV_KEYS_DIR"
    if [ $? -eq 0 ]; then
        echo "✅ Private keys cloned successfully."
    else
        echo "❌ Failed to clone private keys."
    fi
else
    echo "✅ Private keys directory already exists. Skipping clone."
fi

echo ""
# =========================================================
# 2. APPLY SYSTEM PATCHES
# =========================================================

echo "====================================="
echo "Applying System Patches..."
echo "====================================="

# Apply GameSpace live overlay sync patch
if [ -d "$GAMESPACE_DIR" ]; then
    echo "Checking GameSpace patches..."
    cd $GAMESPACE_DIR
    
    # Check if the patch is already applied
    git diff --quiet app/src/main/java/io/chaldeaprjkt/gamespace/utils/GameModeUtils.kt
    
    # If the file hasn't been modified yet, try to patch it
    if [ $? -eq 0 ]; then
        echo "Applying GameSpace sync patch..."
        git apply ../../../$PATCH_DIR/gamespace_sync.patch >/dev/null 2>&1
        
        # Commit the patch so we know it's applied
        if [ $? -eq 0 ]; then
             git add app/src/main/java/io/chaldeaprjkt/gamespace/utils/GameModeUtils.kt
             git commit -m "gamespace-sync: Broadcast mid-game overlay changes to PowerTools"
             echo "✅ GameSpace patch applied and committed."
        else
             echo "⚠️ Warning: Could not apply GameSpace patch. Maybe already applied."
        fi
    else
        echo "✅ GameSpace sync patch already active."
    fi
    
    cd ../../../
else
    echo "⚠️ Warning: $GAMESPACE_DIR not found! Skipping GameSpace patch."
fi

echo "-------------------------------------"

# Apply vendor/gms Android.bp patch
if [ -d "$VENDOR_GMS_DIR" ]; then
    echo "Checking vendor/gms patches..."
    cd $VENDOR_GMS_DIR
    
    # Check if the patch is already applied
    git diff --quiet common/Android.bp
    
    # If the file hasn't been modified yet, try to patch it
    if [ $? -eq 0 ]; then
        echo "Applying vendor/gms Android.bp patch..."
        git apply ../../$PATCH_DIR/vendor_gms_android_bp.patch >/dev/null 2>&1
        
        # Commit the patch so we know it's applied
        if [ $? -eq 0 ]; then
             git add common/Android.bp
             git commit -m "vendor/gms: Remove Dialer and messaging overrides from Android.bp"
             echo "✅ vendor/gms patch applied and committed."
        else
             echo "⚠️ Warning: Could not apply vendor/gms patch. Maybe already applied."
        fi
    else
        echo "✅ vendor/gms patch already active."
    fi
    
    cd ../../
else
    echo "⚠️ Warning: $VENDOR_GMS_DIR not found! Skipping vendor/gms patch."
fi

echo "====================================="
echo "Script execution completed!"
echo "====================================="
