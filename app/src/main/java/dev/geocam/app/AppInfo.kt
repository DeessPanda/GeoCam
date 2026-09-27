package dev.geocam.app

/**
 * Identity details shared by the network layers.
 *
 * [contactUserAgent] is sent to every third-party service. OpenStreetMap's tile
 * usage policy requires an identifying User-Agent that also offers a way to
 * contact the app owner; generic agents get throttled or blocked outright.
 */
object AppInfo {
    const val VERSION = "1.0"
    const val URL = "https://github.com/DeessPanda/GeoCam"
    const val contactUserAgent = "GeoCam/$VERSION ($URL)"
}
