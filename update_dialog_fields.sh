sed -i '4496,4598c\
                    Text("Tournament Type", color = Color.White, fontWeight = FontWeight.Bold)\
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {\
                        listOf("PAID", "FREE").forEach { type ->\
                            val isSelected = if (type == "FREE") entryCurrency == "FREE" else entryCurrency != "FREE"\
                            androidx.compose.material3.FilterChip(\
                                selected = isSelected,\
                                onClick = { \
                                    if (type == "FREE") entryCurrency = "FREE"\
                                    else if (entryCurrency == "FREE") entryCurrency = "CASH"\
                                },\
                                label = { Text(type) },\
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)\
                            )\
                        }\
                    }\
\
                    Spacer(modifier = Modifier.height(8.dp))\
                    Text("Match Format", color = Color.White, fontWeight = FontWeight.Bold)\
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {\
                        listOf("Classic", "Clash Squad", "1v1", "2v2", "4v4").forEach { fmt ->\
                            androidx.compose.material3.FilterChip(\
                                selected = format == fmt,\
                                onClick = { format = fmt },\
                                label = { Text(fmt) },\
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)\
                            )\
                        }\
                    }\
\
                    Spacer(modifier = Modifier.height(8.dp))\
                    if (entryCurrency != "FREE") {\
                        Text("Entry Fee Currency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)\
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\
                            listOf("CASH", "COINS").forEach { type ->\
                                androidx.compose.material3.FilterChip(\
                                    selected = entryCurrency == type,\
                                    onClick = { entryCurrency = type },\
                                    label = { Text(type) },\
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)\
                                )\
                            }\
                        }\
                        OutlinedTextField(\
                            value = entryFee,\
                            onValueChange = { entryFee = it },\
                            label = { Text("Entry Fee Amount") },\
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),\
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),\
                            modifier = Modifier.fillMaxWidth()\
                        )\
                        Spacer(modifier = Modifier.height(8.dp))\
                    } else {\
                        OutlinedTextField(\
                            value = adsRequired,\
                            onValueChange = { adsRequired = it },\
                            label = { Text("Ads Required Watch Target") },\
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),\
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),\
                            modifier = Modifier.fillMaxWidth()\
                        )\
                        Spacer(modifier = Modifier.height(8.dp))\
                    }\
\
\
                    Text("Total Prize Pool Currency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)\
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\
                        listOf("CASH", "COINS").forEach { type ->\
                            androidx.compose.material3.FilterChip(\
                                selected = prizeCurrency == type,\
                                onClick = { prizeCurrency = type },\
                                label = { Text(type) },\
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)\
                            )\
                        }\
                    }\
                    OutlinedTextField(\
                        value = prizePool,\
                        onValueChange = { prizePool = it },\
                        label = { Text("Total Prize Pool Amount") },\
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),\
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),\
                        modifier = Modifier.fillMaxWidth()\
                    )\
                    Spacer(modifier = Modifier.height(8.dp))\
\
                    Row(verticalAlignment = Alignment.CenterVertically) {\
                        androidx.compose.material3.Switch(\
                            checked = isPerKillEnabled,\
                            onCheckedChange = { isPerKillEnabled = it },\
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = NeonGold)\
                        )\
                        Spacer(modifier = Modifier.width(8.dp))\
                        Text("Enable Per Kill Reward", color = Color.White)\
                    }\
                    if (isPerKillEnabled) {\
                        Spacer(modifier = Modifier.height(8.dp))\
                        Text("Per Kill Currency", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)\
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\
                            listOf("CASH", "COINS").forEach { type ->\
                                androidx.compose.material3.FilterChip(\
                                    selected = currencyType == type,\
                                    onClick = { currencyType = type },\
                                    label = { Text(type) },\
                                    colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor = NeonGold, selectedLabelColor = CharcoalBg, labelColor = Color.White)\
                                )\
                            }\
                        }\
                        OutlinedTextField(\
                            value = perKillPrize,\
                            onValueChange = { perKillPrize = it },\
                            label = { Text("Per Kill Reward Amount") },\
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),\
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),\
                            modifier = Modifier.fillMaxWidth()\
                        )\
                    }\
                    Spacer(modifier = Modifier.height(8.dp))\
\
                    OutlinedTextField(\
                        value = rankPrizes,\
                        onValueChange = { rankPrizes = it },\
                        label = { Text("Rank Prizes (e.g. 100 CASH, 50 COINS)") },\
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White),\
                        modifier = Modifier.fillMaxWidth()\
                    )\
                    Spacer(modifier = Modifier.height(8.dp))\
                    Row(verticalAlignment = Alignment.CenterVertically) {\
                        androidx.compose.material3.Switch(\
                            checked = showRewardIndex,\
                            onCheckedChange = { showRewardIndex = it },\
                            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = NeonGold)\
                        )\
                        Spacer(modifier = Modifier.width(8.dp))\
                        Text("Show Reward Index (Rank Prizes)", color = Color.White)\
                    }' app/src/main/java/com/example/ui/EsportsApp.kt
