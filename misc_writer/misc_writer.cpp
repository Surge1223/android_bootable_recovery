/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "misc_writer/misc_writer.h"

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <string>
#include <string_view>
#include <vector>
#include "miscwrite.h"

#include <optional>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/unique_fd.h>
#include <fstab/fstab.h>

#ifndef __ANDROID__
#include <cutils/memory.h>  // for strlcpy
#endif

#include <bootloader_message/bootloader_message.h>

namespace android {
namespace hardware {
namespace google {
namespace pixel {

using android::fs_mgr::Fstab;
using android::fs_mgr::ReadDefaultFstab;

static std::string g_misc_device_for_test;

// Exposed for test purpose.
void SetMiscBlockDeviceForTest(std::string_view misc_device) {
  g_misc_device_for_test = misc_device;
}
static std::string get_misc_blk_device(std::string* err) {
  if (!g_misc_device_for_test.empty()) {
    return g_misc_device_for_test;
  }
  Fstab fstab;
  if (!ReadDefaultFstab(&fstab)) {
    *err = "failed to read default fstab";
    return "";
  }
  for (const auto& entry : fstab) {
    if (entry.mount_point == "/misc") {
      return entry.blk_device;
    }
  }
  *err = "failed to find /misc partition";
  return "";
}

bool write_misc_partition(const void* p, size_t size, const std::string& misc_blk_device,
                          size_t offset, std::string* err) {
  android::base::unique_fd fd(open(misc_blk_device.c_str(), O_WRONLY));
  if (fd == -1) {
    *err = android::base::StringPrintf("failed to open %s: %s", misc_blk_device.c_str(),
                                       strerror(errno));
    return false;
  }
  if (lseek(fd, static_cast<off_t>(offset), SEEK_SET) != static_cast<off_t>(offset)) {
    *err = android::base::StringPrintf("failed to lseek %s: %s", misc_blk_device.c_str(),
                                       strerror(errno));
    return false;
  }
  if (!android::base::WriteFully(fd, p, size)) {
    *err = android::base::StringPrintf("failed to write %s: %s", misc_blk_device.c_str(),
                                       strerror(errno));
    return false;
  }
  if (fsync(fd) == -1) {
    *err = android::base::StringPrintf("failed to fsync %s: %s", misc_blk_device.c_str(),
                                       strerror(errno));
    return false;
  }
  return true;
}

bool MiscWriter::OffsetAndSizeInVendorSpace(size_t offset, size_t size) {
  auto total_size = WIPE_PACKAGE_OFFSET_IN_MISC - VENDOR_SPACE_OFFSET_IN_MISC;
  return size <= total_size && offset <= total_size - size;
}

bool MiscWriter::WriteMiscPartitionVendorSpace(const void* data, size_t size, size_t offset,
                                               std::string* err) {
  if (!OffsetAndSizeInVendorSpace(offset, size)) {
    *err = android::base::StringPrintf("Out of bound write (offset %zu size %zu)", offset, size);
    return false;
  }
  auto misc_blk_device = get_misc_blk_device(err);
  if (misc_blk_device.empty()) {
    return false;
  }
  return write_misc_partition(data, size, misc_blk_device, VENDOR_SPACE_OFFSET_IN_MISC + offset,
                              err);
}

bool MiscWriter::PerformAction(std::optional<size_t> override_offset) {
  size_t offset = 0;
  std::string content;
  switch (action_) {
    case MiscWriterActions::kSetDarkThemeFlag:
    case MiscWriterActions::kClearDarkThemeFlag:
      offset = override_offset.value_or(kThemeFlagOffsetInVendorSpace);
      content = (action_ == MiscWriterActions::kSetDarkThemeFlag)
                    ? kDarkThemeFlag
                    : std::string(strlen(kDarkThemeFlag), 0);
      break;
    case MiscWriterActions::kSetSotaFlag:
    case MiscWriterActions::kClearSotaFlag:
      offset = override_offset.value_or(kSotaFlagOffsetInVendorSpace);
      content = (action_ == MiscWriterActions::kSetSotaFlag) ? kSotaFlag
                                                             : std::string(strlen(kSotaFlag), 0);
      break;
    case MiscWriterActions::kUnset:
      LOG(ERROR) << "The misc writer action must be set";
      return false;
  }

  if (std::string err;
      !WriteMiscPartitionVendorSpace(content.data(), content.size(), offset, &err)) {
    LOG(ERROR) << "Failed to write " << content << " at offset " << offset << " : " << err;
    return false;
  }
  return true;
}

}  // namespace pixel
}  // namespace google
}  // namespace hardware
}  // namespace android
