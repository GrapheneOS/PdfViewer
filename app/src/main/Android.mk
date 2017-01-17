LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := PdfViewer
LOCAL_MODULE_TAGS := optional
LOCAL_SDK_VERSION := current
LOCAL_SRC_FILES := $(call all-java-files-under, java)
LOCAL_PROGUARD_FLAG_FILES += ../../proguard-rules.pro

include $(BUILD_PACKAGE)
