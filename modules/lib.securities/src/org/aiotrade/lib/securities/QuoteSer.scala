/*
 * Copyright (c) 2006-2007, AIOTrade Computing Co. and Contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 
 *  o Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer. 
 *    
 *  o Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution. 
 *    
 *  o Neither the name of AIOTrade Computing Co. nor the names of 
 *    its contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission. 
 *    
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR 
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, 
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.aiotrade.lib.securities

import org.aiotrade.lib.math.timeseries.{DefaultMasterSer,Frequency,SerChangeEvent}
import org.aiotrade.lib.math.timeseries.plottable.Plot

/**
 *
 * @author Caoyuan Deng
 */
class QuoteSer(freq:Frequency) extends DefaultMasterSer(freq) {
    
    private var _shortDescription:String = ""
    var adjusted :Boolean = false
    
    val open   = TimeVar[Float]("O", Plot.Quote)
    val high   = TimeVar[Float]("H", Plot.Quote)
    val low    = TimeVar[Float]("L", Plot.Quote)
    val close  = TimeVar[Float]("C", Plot.Quote)
    val volume = TimeVar[Float]("V", Plot.Volume)
    
    val close_ori = TimeVar[Float]()
    val close_adj = TimeVar[Float]()
    
    override
    protected def createItem(time:Long) :QuoteItem = new QuoteItem(this, time)

    /**
     * @param boolean b: if true, do adjust, else, de adjust
     */
    def adjust(b:Boolean) :Unit = {
        val items1 = items
        var i = 0
        while (i < items1.size) {
            val item = items1(i).asInstanceOf[QuoteItem]
            
            var prevNorm = item.close
            var postNorm = if (b) {
                /** do adjust */
                item.close_adj
            } else {
                /** de adjust */
                item.close_ori
            }
                        
            item.high  = linearAdjust(item.high,  prevNorm, postNorm)
            item.low   = linearAdjust(item.low,   prevNorm, postNorm)
            item.open  = linearAdjust(item.open,  prevNorm, postNorm)
            item.close = linearAdjust(item.close, prevNorm, postNorm)

            i += 1
        }
        
        adjusted = b
        
        val evt = new SerChangeEvent(this, SerChangeEvent.Type.Updated, null, 0, lastOccurredTime)
        fireSerChangeEvent(evt)
    }
    
    /**
     * This function adjusts linear according to a norm
     */
    private def linearAdjust(value:Float, prevNorm:Float, postNorm:Float) :Float = {
        ((value - prevNorm) / prevNorm) * postNorm + postNorm;
    }

    override
    def shortDescription_=(symbol:String) :Unit = {
        this._shortDescription = symbol
    }
    
    override
    def shortDescription :String = {
        if (adjusted) {
            _shortDescription + "(*)"
        } else {
            _shortDescription
        }
    }
    
}




