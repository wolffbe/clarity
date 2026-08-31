package dev.clarity

/**
 * Domain level rules for the filtering VPN. We can only see hostnames (via the
 * DNS query), never the encrypted contents of a request, so blocking works at
 * the granularity of "which server", not "which file". That is enough to cut
 * video CDNs while leaving audio CDNs alone, because they are usually separate
 * hosts.
 *
 * Turn on FilterVpnService query logging (logcat tag "ClarityDNS") to see exactly
 * what a given app resolves on YOUR device, then move hosts between the sets.
 */
object DomainRules {

    /**
     * Blocked video / streaming delivery hosts. A DNS query for any of these,
     * or any subdomain, is answered with NXDOMAIN so the video never loads.
     * Spotify audio hosts (audio*-*.scdn.co, *-akamai-audio, etc.) are NOT in
     * here, so music keeps playing.
     */
    val BLOCKED_SUFFIXES = setOf(
        // Spotify video: canvas loops and video podcasts/clips.
        // Verify on device and extend; these are the known video hosts.
        "canvaz.scdn.co",
        "canvaz-cdn.scdn.co",
        "video-fa.scdn.co",
        "video-akpcw-cdn-spotify-com.akamaized.net",
        "spotifycdn.net",           // used for some video assets
        // YouTube video delivery (search/app still work, playback stream dies)
        "googlevideo.com",
        "youtubei.googleapis.com",
        "yt3.ggpht.com",
        // Generic short form / social video CDNs
        "tiktokcdn.com",
        "tiktokv.com",
        "muscdn.com",
        "fbcdn.net",                // Facebook/Instagram media incl. video
        "cdninstagram.com",
        "video.twimg.com",
        "v.redd.it",
        // Big streaming services
        "nflxvideo.net",            // Netflix
        "aiv-cdn.net",              // Prime Video
        "media-amazon.com",
        "dssedge.com",              // Disney+
        "hulustream.com",
        "ttvnw.net",                // Twitch video
    )

    /**
     * DoH (DNS over HTTPS) endpoints. If an app resolved these it could bypass
     * our DNS filter entirely, so we block the resolvers themselves. We also
     * force system Private DNS (DoT) off from the device owner side.
     */
    val BLOCKED_DOH = setOf(
        "dns.google",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "one.one.one.one",
        "dns.quad9.net",
        "doh.opendns.com",
        "dns.adguard.com",
        "doh.cleanbrowsing.org",
        "dns.nextdns.io",
        "chrome.cloudflare-dns.com",
    )

    private val allBlocked: Set<String> get() = BLOCKED_SUFFIXES + BLOCKED_DOH

    /** True if [host] is, or is a subdomain of, any blocked suffix. */
    fun isBlocked(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return allBlocked.any { suffix ->
            h == suffix || h.endsWith(".$suffix")
        }
    }
}
