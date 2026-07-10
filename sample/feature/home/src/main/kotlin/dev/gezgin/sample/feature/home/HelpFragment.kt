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

// Brownfield Fragment interop: Fragment hiçbir Gezgin arayüzü implement etmez ve parametreli ctor'u YOK
// (framework no-arg ctor + Bundle ile yeniden yaratır). Route/navigator gezginArgs/gezginNav ile enjekte edilir.
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
        view.findViewById<TextView>(R.id.help_topic).text = "Konu: ${args.topic}"
        // nav erişimi tık lambdasına ertelenir — bind (onUpdate) tamamlanmadan okunmamalı.
        view.findViewById<Button>(R.id.help_back).setOnClickListener { nav.backToDashboard() }
    }
}
