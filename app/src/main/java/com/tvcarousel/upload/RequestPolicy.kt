package com.tvcarousel.upload

/** A public marker, not a password. Cross-origin pages cannot send it without a preflight. */
object RequestPolicy {
    fun allowed(host:String?,origin:String?,marker:String?,expectedHost:String)=
        host==expectedHost && (origin==null || origin=="http://$expectedHost") && marker=="TVCarousel"
}
