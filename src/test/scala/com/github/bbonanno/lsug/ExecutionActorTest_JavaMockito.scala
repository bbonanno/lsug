package com.github.bbonanno.lsug

import cats._
import com.github.bbonanno.lsug.ClientError.Unauthorized
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{FreeSpec, Matchers, OptionValues}
import org.scalatestplus.mockito.MockitoSugar

/**
 * Pros:
 *  Don't have to write the stubs
 *  Use of mocks/stubs is standard across the app
 * Cons:
 *  api a bit verbose and non-natural
 *  some java-scala compat issues
 *  still a lot of verifications
 *  I have an extra dependency
 *  No Equality & Prettifier
 */
class ExecutionActorTest_JavaMockito extends FreeSpec with Matchers with OptionValues with TestBuilder with MockitoSugar {

  trait Setup {
    val token1 = AuthToken("good token")
    val token2 = AuthToken("good token2")

    val credentials = Credentials("test-email", "test-key")
    val httpClient  = mock[HttpClient[Id]]
    when(httpClient.login(any())) thenReturn Right(token1)
    val eventLogger = mock[EventLogger[Id]]
    val testObj     = new ExecutionActor(httpClient, eventLogger, credentials)
  }

  "ExecutionActor" - {

    "should login on startup" in new Setup {
      verify(httpClient).login(credentials)
    }

    "should send an order and record the result" in new Setup {
      val status = orderStatus(100.GBP at 1.2999.USD)
      when(httpClient.submitOrder(any())(any())) thenReturn Right(status)
      val order = limitOrder(100.GBP at 1.3.USD)

      testObj ! order

      verify(httpClient).login(credentials)
      verify(httpClient).submitOrder(order)(token1)
      val captor: ArgumentCaptor[Event] = ArgumentCaptor.forClass(classOf[Event])
      verify(eventLogger).record(captor.capture())
      captor.getValue should ===(orderStatusEvent(status))
    }

    "should re-login if the token expires" in new Setup {
      val status = orderStatus(100.GBP at 1.2999.USD)
      when(httpClient.login(any())) thenReturn Right(token2)
      when(httpClient.submitOrder(any())(any())) thenReturn Left(Unauthorized("out!")) thenReturn Right(status)
      val order = limitOrder(100.GBP at 1.3.USD)

      testObj ! order

      verify(httpClient, times(2)).login(credentials)
      verify(httpClient).submitOrder(order)(token1)
      verify(httpClient).submitOrder(order)(token2)
      val captor: ArgumentCaptor[Event] = ArgumentCaptor.forClass(classOf[Event])
      verify(eventLogger).record(captor.capture())
      captor.getValue should ===(orderStatusEvent(status))
    }
  }
}
