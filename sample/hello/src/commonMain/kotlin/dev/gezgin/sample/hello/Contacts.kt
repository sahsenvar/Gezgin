package dev.gezgin.sample.hello

data class Contact(val id: String, val name: String, val title: String)

val CONTACTS =
  listOf(
    Contact("c-1", "Ada Lovelace", "Analytical Engine"),
    Contact("c-2", "Grace Hopper", "Compiler"),
    Contact("c-3", "Barbara Liskov", "Substitution"),
  )
