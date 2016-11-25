/*
 * btc-hotspot
 * Copyright (C) 2016  Andrea Raspitzu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package wallet

import java.io.File

import com.google.protobuf.ByteString
import com.typesafe.scalalogging.slf4j.LazyLogging
import commons.Configuration.WalletConfig._
import commons.Configuration.MiniPortalConfig._
import iptables.IpTablesService
import org.bitcoin.protocols.payments.Protos
import org.bitcoin.protocols.payments.Protos.PaymentRequest
import org.bitcoinj.core.TransactionBroadcast.ProgressCallback
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.protocols.payments.PaymentProtocol
import org.bitcoinj.script.{ScriptBuilder, ScriptOpCodes}
import org.bitcoinj.script.ScriptOpCodes._
import org.bitcoinj.wallet.KeyChain.KeyPurpose
import protocol.Repository
import protocol.domain.{QtyUnit, Offer, Session}
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import commons.Helpers.ScalaConversions._

/**
  * Created by andrea on 16/11/16.
  */
trait WalletServiceComponent extends LazyLogging {

  val walletService: WalletService

  class WalletService {

    val file = new File(walletDir)

    val kit = new WalletAppKit(network, file, walletFileName) {
      override def onSetupCompleted() {
        wallet.importKey(new ECKey)
      }
    }.setAutoStop(true)
     .setUserAgent("paypercom", "0.0.1-alpha")

    if(isEnabled)
      kit.startAsync

    def networkParams:NetworkParameters = kit.params
    def peerGroup:PeerGroup = kit.peerGroup
    def wallet:org.bitcoinj.wallet.Wallet = kit.wallet
    def ownerReceivingAddress:Address = wallet.currentAddress(KeyPurpose.RECEIVE_FUNDS)

    def receivingAddress: String = bytes2hex(wallet.currentReceiveAddress.getHash160)

    def bytes2hex(bytes: Array[Byte]): String = bytes.map("%02x ".format(_)).mkString

    def generatePaymentRequest(session: Session, offerId: String): Future[PaymentRequest] = Future {
      logger.info(s"Issuing payment request for session ${session.id} and offer $offerId")

      val Some(offer) = Repository.allOffers.find(_.offerId == offerId)

      PaymentProtocol.createPaymentRequest(
        networkParams,
        outputsForOffer(offer).asJava,
        s"Please pay ${offer.price} satoshis for ${offer.description}",
        s"http://$miniPortalHost:$miniPortalPort/api/pay/${session.id}",
        offerId.getBytes
      ).build

    }

    def validatePayment(session: Session, offerId:String, payment: Protos.Payment): Future[Protos.PaymentACK] = {

      if(payment.getTransactionsCount != 1)
        throw new IllegalStateException("Too many tx received in payment session")

      val offer = Repository.offerById(offerId)
      if(offer.qty.unit != QtyUnit.minutes)
        throw new NotImplementedError(s"${QtyUnit.MB} not yet implemented")

      val offerRemainingTime = offer.qty.value

      val txBytes = payment.getTransactions(0).toByteArray
      val tx = new Transaction(networkParams, txBytes)
      val broadcast = peerGroup.broadcastTransaction(tx)


      broadcast.setProgressCallback(new ProgressCallback {
        override def onBroadcastProgress(progress: Double): Unit =
          logger.info(s"TX for session ${session.id} broadcast at ${progress * 100}%")
      })

      broadcast.future.toScalaFuture map { broadcastedTx =>

        IpTablesService.enableClient(session.clientMac, offerRemainingTime.toInt)
        PaymentProtocol.createPaymentAck(payment, s"Enjoy, your session will last for ${offerRemainingTime}min")
      }

    }

    def p2pubKeyHash(value:Long, to:Address):ByteString = {

      //Create custom script containing offer's id bytes
//      val scriptOpReturn = new ScriptBuilder().op(OP_RETURN).data("hello".getBytes()).build()

      ByteString.copyFrom(new TransactionOutput(
        networkParams,
        null,
        Coin.valueOf(value),
        to
      ).getScriptBytes)
    }

    def isDust(satoshis:Long) = satoshis >= Transaction.MIN_NONDUST_OUTPUT.getValue

    def outputsForOffer(offer:Offer):List[Protos.Output] = {
      def outputBuilder = Protos.Output.newBuilder

      if(isDust(offer.price))
        throw new IllegalArgumentException(s"Price ${offer.price} is too low, considered dust")


      val ownerOutput =
        outputBuilder
          .setAmount(offer.price)
          .setScript(p2pubKeyHash(
            value = offer.price,
            to = ownerReceivingAddress
          ))
          .build

      List(ownerOutput)
    }




  }


}
