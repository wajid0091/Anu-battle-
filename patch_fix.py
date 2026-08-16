import re

with open('app/src/main/java/com/example/ui/EsportsApp.kt', 'r') as f:
    lines = f.readlines()

# Let's fix the Button around 4945-4955
# We'll just search for `Text("CLOSE", color = Color.White)` or similar to ensure correctness.

content = "".join(lines)
fixed = re.sub(
    r'onClick = \{ showPlayersDialogId = null \},\n\s*colors = ButtonDefaults\.buttonColors\(containerColor = Color\.DarkGray\),\n\s*modifier = Modifier\.fillMaxWidth\(\)\n\s*\) \{\n\s*\}\n\s*Spacer\(modifier = Modifier\.height\(48\.dp\)\)\n\s*\}',
    r'''onClick = { showPlayersDialogId = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("CLOSE", color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(48.dp))''',
    content
)

with open('app/src/main/java/com/example/ui/EsportsApp.kt', 'w') as f:
    f.write(fixed)

