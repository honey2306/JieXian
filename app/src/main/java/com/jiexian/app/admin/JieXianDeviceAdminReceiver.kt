package com.jiexian.app.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Device administrator for the optional enforcement mode.
 *
 * As device owner the app can suspend the packages of an active lock, so the system refuses
 * to start them; this holds even when the accessibility service is killed or its grant is
 * cleared by the ROM, which is the bypass a pure accessibility guard cannot close.
 */
class JieXianDeviceAdminReceiver : DeviceAdminReceiver()
