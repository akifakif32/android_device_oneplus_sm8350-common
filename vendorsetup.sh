#!/bin/bash
#
# apply_patch() mechanism cherry-picked from Jammy555/android_device_oneplus_sm8350-common
# @Sakura (aox doesn't carry it). Auto-sourced by build/envsetup.sh for every
# device dir on the search path -- no manual step needed once synced.

PATCH_DIR="device/oneplus/sm8350-common/patches"

apply_patch() {
    local target_dir=$1
    local patch_file=$2
    local name=$3

    if [ -d "$target_dir" ]; then
        pushd "$target_dir" >/dev/null
        if git apply --reverse --check "$patch_file" >/dev/null 2>&1; then
            echo "${target_dir}: Already applied ($name)"
        elif git apply "$patch_file" >/dev/null 2>&1; then
            echo "${target_dir}: Successfully applied ($name)"
        else
            echo "========================================================================"
            echo " [!] MERGE CONFLICT / PATCH ERROR"
            echo " Target: ${target_dir}"
            echo " Patch Name: ${name}"
            echo " Patch File: ${patch_file}"
            echo " Details of failure:"
            git apply --check "$patch_file"
            echo "========================================================================"
        fi
        popd >/dev/null
    fi
}

apply_patch "frameworks/base" "../../$PATCH_DIR/frameworks_base_camera2_high_speed_ranges_from_supported_target.patch" "camera2: createHighSpeedRequestList() takes the fps ranges from a target whose size is an advertised high speed size instead of whichever target getTargets() lists first, so a privileged camera app recording a larger surface in a constrained high speed session no longer fails at random"
apply_patch "frameworks/base" "../../$PATCH_DIR/frameworks_base_camera2_remember_missing_vendor_tags.patch" "camera2: CameraMetadataNative remembers for 5 s that a key's tag lookup failed instead of repeating the native lookup, error log line and exception on every get()/set() of a vendor key the HAL does not define"
apply_patch "hardware/qcom-caf/sm8350/audio" "../../../../$PATCH_DIR/hardware_qcom_caf_sm8350_audio_spkr_prot_thermalclient_soname.patch" "audio HAL spkr_prot_init(): dlopen libthermalclient.so by soname instead of the fixed /vendor/lib path, so a 64-bit libspkrprot finds the lib64 copy"
apply_patch "hardware/qcom-caf/sm8350/audio" "../../../../$PATCH_DIR/hardware_qcom_caf_sm8350_audio_spatializer_stream_hifi_usecase.patch" "audio HAL adev_open_output_stream(): an output opened FAST|DEEP_BUFFER (what libaudiohal sends for the spatializer stream to HALs below 7.1) takes the hifi usecase instead of the low-latency one the primary output holds"
apply_patch "hardware/qcom-caf/sm8350/audio" "../../../../$PATCH_DIR/hardware_qcom_caf_sm8350_audio_close_pcm_on_failed_start.patch" "audio HAL start_output_stream(): close out->pcm when the start fails after the PCM was opened, so the next start does not find the device busy"
apply_patch "prebuilts/openeuicc-deps" "../../$PATCH_DIR/prebuilts_openeuicc_deps_sdk_version_36.patch" "OpenEUICC prebuilt deps: sdk_version 37 -> 36, the newest finalized SDK this tree has"
apply_patch "frameworks/av" "../../$PATCH_DIR/frameworks_av_camera_hidl_bt2020_override_is_hlg.patch" "cameraservice: complete the bare BT.2020 dataspace (0x60000) the OPLUS SM8350 HAL returns for an HLG video stream to BT.2020 HLG limited, so the recording carries the HLG transfer characteristic"
