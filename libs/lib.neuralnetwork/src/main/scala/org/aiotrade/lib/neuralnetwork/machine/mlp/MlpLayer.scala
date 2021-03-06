/*
 * Copyright (c) 2006-2011, AIOTrade Computing Co. and Contributors
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

package org.aiotrade.lib.neuralnetwork.machine.mlp

import org.aiotrade.lib.neuralnetwork.core.model.Layer
import org.aiotrade.lib.neuralnetwork.machine.mlp.neuron.PerceptronNeuron

/**
 * 
 * @author Caoyuan Deng
 */
abstract class MlpLayer(_nextLayer: Layer, _inputDimension: Int, _nNeurons: Int, _neuronClassName: String, _isHidden: Boolean = true
) extends Layer(_nextLayer, _inputDimension) {
        
  try {
    var i = 0
    while (i < _nNeurons) {
      val neuron = Class.forName(_neuronClassName).newInstance().asInstanceOf[PerceptronNeuron]
      neuron.init(_inputDimension, _isHidden)
      addNeuron(neuron)
        
      i += 1
    }
  } catch {
    case ex: ClassNotFoundException => throw new RuntimeException(ex)
    case ex: InstantiationException => throw new RuntimeException(ex)
    case ex: IllegalAccessException => throw new RuntimeException(ex)
  }
    
  def backPropagateFromNextLayerOrExpectedOutput {
    computeNeuronsDelta
    computeNeuronsGradientAndSumIt
  }
    
  /** 
   * For hidden layer @see MlpHiddenLayer#computeNeuronsDelta()
   * For output layer @see MlpOutputLayer#computeNeuronsDelta()
   */
  protected def computeNeuronsDelta()
    
  def computeNeuronsGradientAndSumIt() {
    neurons foreach {case n: PerceptronNeuron => n.learner.computeGradientAndSumIt}
  }
    
  def adapt(learningRate: Double, momentumRate: Double) {
    neurons foreach {case n: PerceptronNeuron => n.adapt(learningRate, momentumRate)}
  }
    
  override 
  def nextLayer: MlpLayer = super.nextLayer.asInstanceOf[MlpLayer]
}