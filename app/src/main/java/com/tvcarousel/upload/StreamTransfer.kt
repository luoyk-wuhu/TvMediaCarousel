package com.tvcarousel.upload
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/** Fixed-size buffer and Long counters, including files larger than 2 GiB. */
object StreamTransfer {
    fun copyExact(input:InputStream,out:OutputStream,length:Long,checkSpace:()->Unit={}) {
        require(length>=0)
        var remaining=length
        val buffer=ByteArray(128*1024)
        while(remaining>0) {
            checkSpace()
            val n=input.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt())
            if(n<0)throw EOFException("Upload interrupted with $remaining bytes remaining")
            if(n==0)throw java.io.IOException("Input made no progress")
            out.write(buffer,0,n);remaining-=n
        }
    }
}
