import os
import subprocess
import shutil

output_dir = "termux-packages/output"
extract_dir = "bootstrap_root"

if os.path.exists(extract_dir):
    shutil.rmtree(extract_dir)
os.makedirs(extract_dir)

# Get all deb files
deb_files = [f for f in os.listdir(output_dir) if f.endswith(".deb")]

print(f"Extracting {len(deb_files)} deb files...")
for deb in deb_files:
    deb_path = os.path.join(output_dir, deb)
    print(f"Unpacking {deb}...")
    
    # Create temp directory for this deb
    temp_deb_dir = "temp_deb"
    if os.path.exists(temp_deb_dir):
        shutil.rmtree(temp_deb_dir)
    os.makedirs(temp_deb_dir)
    
    # Extract deb archive
    subprocess.run(["ar", "x", os.path.abspath(deb_path)], cwd=temp_deb_dir, check=True)
    
    # Find data archive (can be data.tar.xz, data.tar.gz, data.tar.zst)
    data_archive = None
    for f in os.listdir(temp_deb_dir):
        if f.startswith("data.tar"):
            data_archive = f
            break
            
    if data_archive:
        # Extract data archive into bootstrap_root
        archive_path = os.path.join(temp_deb_dir, data_archive)
        subprocess.run(["tar", "-xf", archive_path, "-C", os.path.abspath(extract_dir)], check=True)
        
    shutil.rmtree(temp_deb_dir)

print("Extraction complete.")
