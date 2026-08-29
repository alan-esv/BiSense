package com.example.myapplicationbetat

import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val homeFragment = HomeFragment()
    private val graphsFragment = GraphsFragment()
    private val historyFragment = HistoryFragment()
    private val reportsFragment = ReportsFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Encontrar la barra de navegación
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        // Cargar el HomeFragment por defecto al iniciar
        if (savedInstanceState == null) {
            loadFragment(homeFragment)
        }

        // Establecer el "listener" para los clics en la barra
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(homeFragment)
                }
                R.id.nav_graphs -> {
                    loadFragment(graphsFragment)
                }
                R.id.nav_history -> {
                    loadFragment(historyFragment)
                }
                R.id.nav_reports -> {
                    loadFragment(reportsFragment)
                }
            }

            animateBottomNav(bottomNavigation, item.itemId)

            true
        }
    }

    // Función para reemplazar el fragment en el contenedor
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun animateBottomNav(bottomNav: BottomNavigationView, selectedItemId: Int) {
        val menuView = bottomNav.getChildAt(0) as ViewGroup

        for (i in 0 until menuView.childCount) {
            val item = menuView.getChildAt(i)

            val isSelected = bottomNav.menu.getItem(i).itemId == selectedItemId

            val scale = if (isSelected) 1.1f else 0.95f

            item.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(200)
                .start()
        }
    }
}