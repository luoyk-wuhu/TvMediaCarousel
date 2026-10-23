package com.tvcarousel
import com.tvcarousel.media.NaturalOrder
import com.tvcarousel.upload.StreamTransfer
import com.tvcarousel.upload.RequestPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class CoreTests {
    @Test fun passwordlessApiAcceptsSameOriginWithoutPin() {
        assertTrue(RequestPolicy.allowed("192.168.1.2:8080","http://192.168.1.2:8080","TVCarousel","192.168.1.2:8080"))
        assertTrue(RequestPolicy.allowed("192.168.1.2:8080",null,"TVCarousel","192.168.1.2:8080"))
    }
    @Test fun passwordlessApiRejectsCrossSiteAndSimpleRequests() {
        assertFalse(RequestPolicy.allowed("192.168.1.2:8080","https://evil.example","TVCarousel","192.168.1.2:8080"))
        assertFalse(RequestPolicy.allowed("evil.example",null,"TVCarousel","192.168.1.2:8080"))
        assertFalse(RequestPolicy.allowed("192.168.1.2:8080",null,null,"192.168.1.2:8080"))
    }
    @Test fun filenamesWithHugeNumbersSortWithoutOverflow() {
        val names=listOf("10.jpg","2.jpg","1.jpg","999999999999999999999999999.jpg","1000000000000000000000000000.jpg")
        assertEquals(listOf("1.jpg","2.jpg","10.jpg","999999999999999999999999999.jpg","1000000000000000000000000000.jpg"),names.sortedWith(NaturalOrder))
    }
    @Test fun leadingZerosAndMixedCaseHaveDeterministicOrder() {
        val names=listOf("a02.JPG","a2.jpg","A2.jpg","a000.jpg","a0.jpg")
        for(a in names)for(b in names)assertEquals(-Integer.signum(NaturalOrder.compare(b,a)),Integer.signum(NaturalOrder.compare(a,b)))
        for(a in names)for(b in names)for(c in names)if(NaturalOrder.compare(a,b)<=0&&NaturalOrder.compare(b,c)<=0)assertTrue(NaturalOrder.compare(a,c)<=0)
    }
    @Test fun streamsBeyondTwoGiBWithBoundedBuffers() {
        val length=2L*1024*1024*1024+123
        var remaining=length;var largest=0;var written=0L
        val source=object:InputStream() {
            override fun read():Int=error("Buffer required")
            override fun read(b:ByteArray,off:Int,len:Int):Int {largest=maxOf(largest,len);if(remaining==0L)return -1;val n=minOf(len.toLong(),remaining).toInt();remaining-=n;return n}
        }
        val sink=object:OutputStream() {override fun write(b:Int) {written++};override fun write(b:ByteArray,off:Int,len:Int) {written+=len}}
        StreamTransfer.copyExact(source,sink,length)
        assertEquals(length,written);assertEquals(0L,remaining);assertTrue(largest<=128*1024)
    }
    @Test(expected=EOFException::class) fun interruptedUploadsFail() {StreamTransfer.copyExact(ByteArrayInputStream(byteArrayOf(1,2)),ByteArrayOutputStream(),3)}
    @Test fun receiverReadsOnlyDeclaredLength() {val source=ByteArrayInputStream(byteArrayOf(1,2,3));val sink=ByteArrayOutputStream();StreamTransfer.copyExact(source,sink,2);assertArrayEquals(byteArrayOf(1,2),sink.toByteArray());assertEquals(3,source.read())}
    @Test(expected=IOException::class) fun diskSpaceFailureStopsTransfer() {StreamTransfer.copyExact(ByteArrayInputStream(byteArrayOf(1)),ByteArrayOutputStream(),1) {throw IOException("disk full")}}
}
