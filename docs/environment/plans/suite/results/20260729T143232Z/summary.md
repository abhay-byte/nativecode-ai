# Regression results — 20260729T143232Z

- ADB: `192.168.1.52:43055`
- Envs: `proot-distro,proot-fast,chroot`
- Offline: `0`
- Package: `com.ivarna.nativecode`

| P0 | P0-01 | host_package | PASS | export TERMUX_APP__PACKAGE_NAME="com.ivarna.nativecode" |
| P0 | P0-02 | proot_binary_elf | PASS | /data/data/com.ivarna.nativecode/files/usr/bin/proot: ELF shared object, 64-bit LSB arm64, dynamic (/system/bin/linker64), for Android 24, built by NDK r29 (14206865), stripped |
| P0 | P0-11 | distro_still_works | PASS | cmd true |
| P0 | P0-12 | chroot_still_works | PASS | chroot true |
| P0 | P0-13 | no_termux_default | PASS | /data/data/com.ivarna.nativecode/files/usr/var/lib/proot-distro/containers/debian/rootfs |
| P0 | P0-14 | kill_clean | PASS | proot not wrapper |
| P0 | P1-52 | no_drm_glmark | PASS | no drm glmark in suite |
### env=proot-distro script=p0
RESULT proot-distro p0 UNKNOWN (see log)
### env=proot-distro script=p1_dev
RESULT proot-distro p1_dev UNKNOWN (see log)
### env=proot-distro script=p1_ai
RESULT proot-distro p1_ai UNKNOWN (see log)
### env=proot-fast script=p0
RESULT proot-fast p0 UNKNOWN (see log)
### env=proot-fast script=p1_dev
RESULT proot-fast p1_dev UNKNOWN (see log)
### env=proot-fast script=p1_ai
RESULT proot-fast p1_ai UNKNOWN (see log)
### env=chroot script=p0
RESULT chroot p0 SUMMARY pass=13 fail=0 skip=0
### env=chroot script=p1_dev
RESULT chroot p1_dev SUMMARY pass=12 fail=0 skip=4
### env=chroot script=p1_ai
RESULT chroot p1_ai SUMMARY pass=10 fail=0 skip=5

## Harness note
Guest scripts print PASS/FAIL lines; host P0 recorded in table above.
Exit policy: fail if any guest log contains FAIL lines or host P0 failed.
REGRESSION OVERALL: PASS
