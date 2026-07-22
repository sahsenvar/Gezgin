package dev.gezgin.sample.app

import android.app.Application
import dev.gezgin.core.fragment.Gezgin
import dev.gezgin.sample.navigation.gezginJson

// @FragmentScreen (HelpFragment) gerçek process-death sonrası gezginArgs'ı decode edebilsin diye
// app-Json'u
// FragmentManager restore'undan ÖNCE (process açılışı) kaydeder. Bkz. Gezgin.initFragmentInterop
// KDoc'u.
class ShowcaseApp : Application() {
  override fun onCreate() {
    super.onCreate()
    Gezgin.initFragmentInterop(gezginJson)
  }
}
