import re

with open('app/src/main/java/com/example/ui/EsportsApp.kt', 'r') as f:
    content = f.read()

start_idx = content.find('fun AdminTournamentsCreatorTab')
if start_idx != -1:
    end_idx = content.find('fun ', start_idx + 10)
    if end_idx == -1: end_idx = len(content)
    
    dialog_content = content[start_idx:end_idx]
    
    idx = 0
    count = 0
    while True:
        idx = dialog_content.find('OutlinedTextField(', idx)
        if idx == -1:
            break
            
        paren_count = 0
        end_field_idx = -1
        for i in range(idx + 18, len(dialog_content)):
            if dialog_content[i] == '(':
                paren_count += 1
            elif dialog_content[i] == ')':
                if paren_count == 0:
                    end_field_idx = i
                    break
                paren_count -= 1
                
        if end_field_idx != -1:
            count += 1
            block = dialog_content[idx:end_field_idx+1]
            
            is_multiline = 'maxLines' in block
            is_last = 'Registration Open Time' in block
            action = "ImeAction.Done" if is_last else "ImeAction.Next"
            
            new_block = block
            if not is_multiline and 'singleLine' not in new_block:
                new_block = re.sub(r'modifier\s*=\s*Modifier\.fillMaxWidth\(\)', 'modifier = Modifier.fillMaxWidth(), singleLine = true', new_block)
                
            if 'keyboardOptions' in new_block:
                if 'imeAction' not in new_block:
                    new_block = re.sub(r'keyboardOptions\s*=\s*KeyboardOptions\(([^)]*)\)', r'keyboardOptions = KeyboardOptions(\1, imeAction = ' + action + ')', new_block)
                    new_block = new_block.replace('(, ', '(').replace(', ,', ',')
            else:
                new_block = re.sub(r'modifier\s*=\s*Modifier', 'keyboardOptions = KeyboardOptions(imeAction = ' + action + '),\n                            modifier = Modifier', new_block)
            
            dialog_content = dialog_content[:idx] + new_block + dialog_content[end_field_idx+1:]
            idx += len(new_block)
        else:
            idx += 18

    print(f"Found {count} OutlinedTextFields.")
    content = content[:start_idx] + dialog_content + content[end_idx:]
    
    with open('app/src/main/java/com/example/ui/EsportsApp.kt', 'w') as f:
        f.write(content)
else:
    print("Could not find dialog")

