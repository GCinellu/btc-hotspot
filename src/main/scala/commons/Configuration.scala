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

package commons

import org.bitcoinj.core.NetworkParameters

/**
  * Created by andrea on 09/09/16.
  */
object Configuration {

  lazy val config = com.typesafe.config.ConfigFactory.load()

  lazy val env = config.getString("env")

  object WalletConfig {
    val isEnabled = config.getBoolean(s"wallet.$env.enabled")
    val network:NetworkParameters = NetworkParameters.fromID(config.getString(s"wallet.$env.net"))
    val walletFileName = config.getString(s"wallet.$env.walletFile")
    val walletDir = config.getString(s"wallet.$env.walletDir")
  }

  object MiniPortalConfig {
    val staticFilesDir = config.getString(s"miniportal.$env.staticFilesDir")
    val miniPortalHost = config.getString(s"miniportal.$env.host")
    val miniPortalPort = config.getInt(s"miniportal.$env.port")
    val miniPortalIndex = config.getString(s"miniportal.$env.index")
  }
}

