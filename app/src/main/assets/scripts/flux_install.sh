#!/bin/bash
# flux_install_adapted.sh
# Install and configure PRoot distro with FluxLinux setup for com.ivarna.nativecode

DISTRO=$1
SETUP_B64=$2

# Set critical environment variables for proot-distro
export TERMUX_APP__PACKAGE_NAME="com.ivarna.nativecode"
export TERMUX__PREFIX="/data/data/com.ivarna.nativecode/files/usr"
export TERMUX__HOME="/data/data/com.ivarna.nativecode/files/home"
export HOME="/data/data/com.ivarna.nativecode/files/home"
export LD_LIBRARY_PATH="/data/data/com.ivarna.nativecode/files/usr/lib"

# Load full Termux environment
source /data/data/com.ivarna.nativecode/files/usr/etc/profile

echo "FluxLinux: Debugging Environment:"
echo "HOME=$HOME"
echo "PREFIX=$PREFIX"
echo "TERMUX__HOME=$TERMUX__HOME"
echo "TERMUX__PREFIX=$TERMUX__PREFIX"
echo "TERMUX_APP__PACKAGE_NAME=$TERMUX_APP__PACKAGE_NAME"
echo "LD_LIBRARY_PATH=$LD_LIBRARY_PATH"
echo "----------------------------------------"

echo "FluxLinux: Installing $DISTRO..."

if [ "$DISTRO" == "termux" ]; then
    echo "FluxLinux: Native Termux Mode"
    EXIT_CODE=0
else
    # Check if distro is already installed by looking for its rootfs
    DISTRO_ROOTFS="/data/data/com.ivarna.nativecode/files/home/.local/share/proot-distro/containers/$DISTRO"
    
    if [ -d "$DISTRO_ROOTFS" ]; then
        echo "FluxLinux: $DISTRO already installed. Skipping base installation."
        EXIT_CODE=0
    else
        echo "FluxLinux: Installing $DISTRO base system..."
        python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro install $DISTRO
        EXIT_CODE=$?
    fi
fi

if [ $EXIT_CODE -eq 0 ]; then
    echo "FluxLinux: Install Successful!"
    if [ ! -z "$SETUP_B64" ] && [ "$SETUP_B64" != "null" ]; then
        echo "FluxLinux: Configuring..."
        # Decode setup script to shared tmp directory
        echo "$SETUP_B64" | base64 -d > /data/data/com.ivarna.nativecode/files/usr/tmp/flux_setup_temp.sh
        chmod +x /data/data/com.ivarna.nativecode/files/usr/tmp/flux_setup_temp.sh
        
        if [ "$DISTRO" == "termux" ]; then
            # Run directly in Termux
            bash /data/data/com.ivarna.nativecode/files/usr/tmp/flux_setup_temp.sh
            SETUP_EXIT=$?
        else
            # Move it to a shared location readable by proot
            python /data/data/com.ivarna.nativecode/files/usr/bin/proot-distro login $DISTRO --shared-tmp -- bash -c "bash /tmp/flux_setup_temp.sh $DISTRO"
            SETUP_EXIT=$?
        fi
        
        rm -f /data/data/com.ivarna.nativecode/files/usr/tmp/flux_setup_temp.sh
 
        if [ $SETUP_EXIT -ne 0 ]; then
             echo "FluxLinux: Configuration/Setup Script Failed!"
             exit 1
        fi
        
        echo "FluxLinux: Configuration Complete!"
    fi
    
    # Create marker file to track installation
    touch "/data/data/com.ivarna.nativecode/files/home/.fluxlinux_distro_${DISTRO}_installed"
    echo "Distro installation and configuration completed successfully!"
else
    echo "FluxLinux: Install Failed with code $EXIT_CODE!"
    exit 1
fi
