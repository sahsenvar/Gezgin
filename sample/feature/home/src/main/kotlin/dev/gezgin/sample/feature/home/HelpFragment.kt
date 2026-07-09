package dev.gezgin.sample.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import dev.gezgin.core.annotation.FragmentScreen
import dev.gezgin.core.fragment.gezginArgs
import dev.gezgin.core.fragment.gezginNav
import dev.gezgin.sample.navigation.HelpNavigator
import dev.gezgin.sample.navigation.HomeGraph.HelpScreenRoute

/**
 * Faz 6.4 — brownfield Fragment interop örneği (§11.1). "Henüz Compose'a taşınmamış" legacy bir yardım
 * ekranı: içerik bir `@Screen` composable DEĞİL, gerçek bir View hiyerarşisi (XML `fragment_help.xml`
 * inflate edilir, `findViewById` ile bağlanır — Compose YOK). Bu, Fragment interop'un GERÇEK senaryosudur:
 * elde zaten var olan bir View-tabanlı Fragment'ı, yeniden yazmadan Gezgin'in Nav3 back-stack'ine leaf
 * olarak sokmak.
 *
 * Fragment hiçbir Gezgin arayüzü implement ETMEZ (`class HelpFragment : Fragment()` — ekstra supertype yok);
 * parametreli ctor YOK (framework no-arg ctor + Bundle ile yeniden yaratır). Route ve navigator iki delege
 * ile teslim edilir:
 * - `gezginArgs<HelpScreenRoute>()` → route'u Fragment'ın kendi `arguments` Bundle'ından decode eder (§11.1,
 *   "route Bundle'dan → PD-safe"). `arguments` örnekleme anında kurulduğundan `onViewCreated`'da güvenle
 *   okunur (`onUpdate` zamanlamasından bağımsız).
 * - `gezginNav<HelpNavigator>()` → bind sonrası (`AndroidFragment.onUpdate`) instance-anahtarlı registry'den
 *   canlı navigator'ı okur. Bu yüzden `nav` yalnız kullanıcı ETKİLEŞİMİNDE (buton tık'ı) okunur — o an
 *   bind kesinlikle tamamlanmıştır; delege lazy olduğundan `onViewCreated`'da erken okunmaz.
 */
@FragmentScreen(HelpScreenRoute::class)
class HelpFragment : Fragment() {

    private val args by gezginArgs<HelpScreenRoute>()
    private val nav by gezginNav<HelpNavigator>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.fragment_help, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // gezginArgs — route verisini gerçek bir TextView'a yansıt (log satırı değil, görünür içerik).
        view.findViewById<TextView>(R.id.help_topic).text = "Konu: ${args.topic}"
        // gezginNav — canlı navigator'ın üretilen `backToDashboard()` kenarı (HelpScreenRoute'un
        // @BackTo(DashboardScreenRoute) declarasyonundan). `nav` erişimi tık lambdasına ertelenir (bind-safe).
        view.findViewById<Button>(R.id.help_back).setOnClickListener { nav.backToDashboard() }
    }
}
