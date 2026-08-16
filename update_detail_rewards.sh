sed -i '1771c\
                                    Text(if (tournament.currencyType == "COINS") "${tournament.perKillPrize.toInt()} Coins" else "Rs.${tournament.perKillPrize}", color = NeonGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)' app/src/main/java/com/example/ui/EsportsApp.kt

sed -i '1788,1789c\
                                            val displayText = if (safePrize.contains("CASH", ignoreCase = true) || safePrize.contains("COIN", ignoreCase = true)) safePrize.uppercase() else if (tournament.prizeCurrency == "COINS") "$safePrize Coins" else "Rs.$safePrize"\
                                            Text(displayText, color = MintGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)\
                                        }' app/src/main/java/com/example/ui/EsportsApp.kt
