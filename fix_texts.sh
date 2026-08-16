#!/bin/bash

# Fix Home Tournament Card Prize Pool (1293-1296)
sed -i '1292,1297c\
                        Text(\
                            text = if (tournament.prizeCurrency == "COINS") "${tournament.prizePool.toInt()} Coins" else "Rs.${tournament.prizePool.toInt()}",\
' app/src/main/java/com/example/ui/EsportsApp.kt

# Fix Home Tournament Card Entry Fee (1306-1311)
sed -i '1306,1311c\
                        val entryText = when (tournament.entryCurrency) {\
                            "COINS" -> "${tournament.entryFee.toInt()} Coins"\
                            "FREE" -> "FREE"\
                            else -> if (tournament.entryFee == 0.0) "FREE" else "Rs.${tournament.entryFee.toInt()}"\
                        }' app/src/main/java/com/example/ui/EsportsApp.kt

# Fix Detail Screen Entry Fee (1417-1422)
sed -i '1417,1422c\
                            val entryText = when (tournament.entryCurrency) {\
                                "COINS" -> "${tournament.entryFee.toInt()} Coins"\
                                "FREE" -> "FREE"\
                                else -> if (tournament.entryFee == 0.0) "FREE" else "Rs.${tournament.entryFee.toInt()}"\
                            }' app/src/main/java/com/example/ui/EsportsApp.kt

# Fix Detail Screen Prize Pool Card (1646-1648)
sed -i '1646,1648c\
                            Text(\
                                text = if (tournament.prizeCurrency == "COINS") "${tournament.prizePool.toInt()} Coins" else "Rs.${tournament.prizePool.toInt()}",\
                                color = Color.White,' app/src/main/java/com/example/ui/EsportsApp.kt

# Fix adRequiredButNotDone (1446)
sed -i 's/val adRequiredButNotDone = tournament.currencyType == "FREE"/val adRequiredButNotDone = tournament.entryCurrency == "FREE"/' app/src/main/java/com/example/ui/EsportsApp.kt

