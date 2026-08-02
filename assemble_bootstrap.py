import os
import subprocess
import shutil
import tarfile

script_dir = os.path.dirname(os.path.abspath(__file__))
output_dir = os.path.join(script_dir, "termux-packages/output")
extract_dir = os.path.join(script_dir, "bootstrap_root")
target_prefix = "data/data/com.zenithblue.nativecode/files"
loader_src = os.path.join(script_dir, "shell-loader-debug.apk")

# List of packages needed for host runtime and GUI services
PACKAGES = [
    # Shell & tools
    "bash",
    "termux-exec",
    "coreutils",
    "findutils",
    "grep",
    "sed",
    "psmisc",
    "procps",
    "curl",
    "ca-certificates",
    "tar",
    "xz-utils",
    "python",
    "termux-am",
    "termux-tools",
    "proot",
    "proot-distro",
    "pulseaudio",
    "xkeyboard-config",
    
    # Core Libraries
    "libandroid-support",
    "readline",
    "ncurses",
    "libtalloc",
    "libcurl",
    "openssl",
    "libnghttp2",
    "libssh2",
    "zlib",
    "libidn2",
    "libunistring",
    "libiconv",
    "libunbound",
    "libnettle",
    "libgmp",
    "liblzma",
    "libc++",
    
    # PulseAudio Libraries
    "libandroid-shmem",
    "libsndfile",
    "libvorbis",
    "libogg",
    "flac",
    "libopus",
    "speexdsp",
    "dbus",
    "libexpat",
    "libltdl",
    "libcap",
    "libcap-ng",
    "libevent",
    "glib",
    "pcre2",
    "libffi",
    
    # Python Libraries
    "libsqlite",
    "libbz2",
    "gdbm",  # provides libgdbm
    "libandroid-selinux",
    "libandroid-glob",
    "libacl",
    "libx11",
    "libxau",
    "libxcb",
    "libxdmcp"
]

def clean_and_prepare():
    if os.path.exists(extract_dir):
        print(f"[*] Removing existing {extract_dir}...")
        shutil.rmtree(extract_dir)
    os.makedirs(extract_dir)

def extract_debs():
    # Find all debs matching our packages list
    all_files = os.listdir(output_dir)
    selected_debs = []
    
    for pkg in PACKAGES:
        match = None
        for f in all_files:
            if f.startswith(f"{pkg}_") and f.endswith(".deb"):
                match = f
                break
        if match:
            selected_debs.append(match)
        else:
            print(f"[!] Warning: Package {pkg} not found in output directory!")

    print(f"[*] Extracting {len(selected_debs)} packages...")
    for deb in selected_debs:
        deb_path = os.path.join(output_dir, deb)
        print(f"  Unpacking {deb}...")
        
        temp_deb_dir = os.path.join(extract_dir, "temp_deb")
        if os.path.exists(temp_deb_dir):
            shutil.rmtree(temp_deb_dir)
        os.makedirs(temp_deb_dir)
        
        subprocess.run(["ar", "x", os.path.abspath(deb_path)], cwd=temp_deb_dir, check=True)
        
        data_archive = None
        for f in os.listdir(temp_deb_dir):
            if f.startswith("data.tar"):
                data_archive = f
                break
                
        if data_archive:
            archive_path = os.path.join(temp_deb_dir, data_archive)
            subprocess.run(["tar", "-xf", archive_path, "-C", os.path.abspath(extract_dir)], check=True)
            
        shutil.rmtree(temp_deb_dir)

def add_loader():
    dest_dir = os.path.join(extract_dir, target_prefix, "usr/libexec/termux-x11")
    os.makedirs(dest_dir, exist_ok=True)
    dest_path = os.path.join(dest_dir, "loader.apk")
    print(f"[*] Copying loader.apk from {loader_src} to {dest_path}...")
    shutil.copy2(loader_src, dest_path)

def verify_bootstrap():
    print("[*] Verifying critical files in bootstrap...")
    required_paths = [
        "usr/bin/bash",
        "usr/bin/python",
        "usr/bin/proot",
        "usr/bin/proot-distro",
        "usr/bin/pulseaudio",
        "usr/bin/pkill",
        "usr/lib/pulseaudio/modules/module-native-protocol-tcp.so",
        "usr/libexec/termux-x11/loader.apk"
    ]
    
    all_ok = True
    for path in required_paths:
        full_path = os.path.join(extract_dir, target_prefix, path)
        if os.path.exists(full_path):
            print(f"  [OK] {path}")
        else:
            print(f"  [MISSING] {path}")
            all_ok = False
            
    # Check xkb config
    xkb_path = os.path.join(extract_dir, target_prefix, "usr/share/X11/xkb")
    xkb_config_path = os.path.join(extract_dir, target_prefix, "usr/share/xkeyboard-config-2")
    if os.path.exists(xkb_path) or os.path.exists(xkb_config_path):
        print("  [OK] XKB Config present")
    else:
        print("  [MISSING] XKB Config")
        all_ok = False
        
    if not all_ok:
        raise Exception("Verification failed. Some required bootstrap files are missing.")

def create_tarball():
    tar_path = os.path.join(script_dir, "app/src/main/assets/bootstrap.tar")
    print(f"[*] Packaging bootstrap into {tar_path}...")
    
    files_dir = os.path.join(extract_dir, target_prefix)
    cwd = os.getcwd()
    try:
        os.chdir(files_dir)
        subprocess.run(["tar", "-cf", tar_path, "usr"], check=True)
    finally:
        os.chdir(cwd)
        
    print(f"[*] Tarball created successfully. Size: {os.path.getsize(tar_path) / 1024 / 1024:.2f} MB")

def copy_to_jni_libs():
    jni_dir = os.path.join(script_dir, "app/src/main/jniLibs/arm64-v8a")
    os.makedirs(jni_dir, exist_ok=True)
    
    mapping = {
        "usr/bin/proot": "libproot.so",
        "usr/bin/bash": "libbash.so",
        "usr/libexec/proot/loader": "libloader.so",
        "usr/libexec/proot/loader32": "libloader32.so"
    }
    
    print("[*] Copying critical binaries to jniLibs...")
    for src_rel, dest_name in mapping.items():
        src_path = os.path.join(extract_dir, target_prefix, src_rel)
        dest_path = os.path.join(jni_dir, dest_name)
        if os.path.exists(src_path):
            print(f"  Copying {src_rel} -> {dest_path}")
            shutil.copy2(src_path, dest_path)
            os.chmod(dest_path, 0o755)
        else:
            print(f"  [WARN] Source {src_rel} not found!")

if __name__ == "__main__":
    clean_and_prepare()
    extract_debs()
    add_loader()
    verify_bootstrap()
    copy_to_jni_libs()
    create_tarball()
    print("[*] Bootstrap assembly completed successfully!")
