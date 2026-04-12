$BASE_URL  = "http://localhost:8080/api/v1"
$TENANT_ID = "00000000-0000-0000-0000-000000000001"
$EMAIL     = "admin@dipdv.dev"
$PASSWORD  = "dipdv@2025"

Write-Host "=== SMOKE TESTS US03.3 ==="

# STEP 0: Login
$loginBody = '{"tenantId":"' + $TENANT_ID + '","email":"' + $EMAIL + '","password":"' + $PASSWORD + '"}'
$loginRes = Invoke-RestMethod -Uri "$BASE_URL/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$TOKEN = $loginRes.token
Write-Host "[0] Login OK. Token: $($TOKEN.Substring(0,40))..."

$H = @{Authorization = "Bearer $TOKEN"; "Content-Type" = "application/json"}

# STEP 1: POST /modifier-groups
$g1 = '{"name":"Adicionais","minSelect":0,"maxSelect":3}'
$r1 = Invoke-WebRequest -Uri "$BASE_URL/modifier-groups" -Method Post -Body $g1 -Headers $H -UseBasicParsing
$GROUP_ID = ($r1.Content | ConvertFrom-Json).id
Write-Host "[1] POST /modifier-groups -> $($r1.StatusCode)"
Write-Host "    $($r1.Content)"

# STEP 2: POST /modifier-groups/{id}/options
$o1 = '{"name":"Bacon","priceAddition":3.50,"maxQuantity":3}'
$r2 = Invoke-WebRequest -Uri "$BASE_URL/modifier-groups/$GROUP_ID/options" -Method Post -Body $o1 -Headers $H -UseBasicParsing
Write-Host "[2] POST /modifier-groups/$GROUP_ID/options -> $($r2.StatusCode)"
Write-Host "    $($r2.Content)"

# STEP 3: Get products list
$r3 = Invoke-WebRequest -Uri "$BASE_URL/products?page=0&size=5" -Method Get -Headers $H -UseBasicParsing
$pjson = $r3.Content | ConvertFrom-Json
if ($pjson.content -and $pjson.content.Count -gt 0) {
    $PRODUCT_ID = $pjson.content[0].id
} elseif ($pjson.Count -gt 0) {
    $PRODUCT_ID = $pjson[0].id
} else {
    $catBody = '{"name":"TestCat99","active":true,"position":99}'
    $catR = Invoke-WebRequest -Uri "$BASE_URL/categories" -Method Post -Body $catBody -Headers $H -UseBasicParsing
    $CAT_ID = ($catR.Content | ConvertFrom-Json).id
    $pBody = '{"name":"X-Test","price":25.90,"categoryId":"' + $CAT_ID + '","stockQuantity":100,"stockMinLevel":5}'
    $pR = Invoke-WebRequest -Uri "$BASE_URL/products" -Method Post -Body $pBody -Headers $H -UseBasicParsing
    $PRODUCT_ID = ($pR.Content | ConvertFrom-Json).id
    Write-Host "[3] Produto criado: $PRODUCT_ID"
}
Write-Host "[3] PRODUCT_ID = $PRODUCT_ID"

# STEP 4: Link group to product
try {
    $r4 = Invoke-WebRequest -Uri "$BASE_URL/products/$PRODUCT_ID/modifiers/$GROUP_ID" -Method Post -Headers $H -UseBasicParsing
    Write-Host "[4] POST /products/$PRODUCT_ID/modifiers/$GROUP_ID -> $($r4.StatusCode)"
    Write-Host "    $($r4.Content)"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Write-Host "[4] POST /products/$PRODUCT_ID/modifiers/$GROUP_ID -> $code"
}

# STEP 5: GET /products/{id}/modifiers
$r5 = Invoke-WebRequest -Uri "$BASE_URL/products/$PRODUCT_ID/modifiers" -Method Get -Headers $H -UseBasicParsing
Write-Host "[5] GET /products/$PRODUCT_ID/modifiers -> $($r5.StatusCode)"
Write-Host "    $($r5.Content)"

# STEP 6: Simple product (no modifiers)
$catList = Invoke-WebRequest -Uri "$BASE_URL/categories?page=0&size=5" -Method Get -Headers $H -UseBasicParsing
$cjson = $catList.Content | ConvertFrom-Json
$CAT_SIMPLE = if ($cjson.content) { $cjson.content[0].id } else { $cjson[0].id }
$sp = '{"name":"ProdutoSimplesTest","price":5.00,"categoryId":"' + $CAT_SIMPLE + '","stockQuantity":10,"stockMinLevel":0}'
$spR = Invoke-WebRequest -Uri "$BASE_URL/products" -Method Post -Body $sp -Headers $H -UseBasicParsing
$SIMPLE_ID = ($spR.Content | ConvertFrom-Json).id
$r6 = Invoke-WebRequest -Uri "$BASE_URL/products/$SIMPLE_ID/modifiers" -Method Get -Headers $H -UseBasicParsing
Write-Host "[6] GET /products/$SIMPLE_ID/modifiers -> $($r6.StatusCode) (esperado: [])"
Write-Host "    $($r6.Content)"

# STEP 7: minSelect > maxSelect -> 400
try {
    $bad = '{"name":"Invalido","minSelect":3,"maxSelect":1}'
    $r7 = Invoke-WebRequest -Uri "$BASE_URL/modifier-groups" -Method Post -Body $bad -Headers $H -UseBasicParsing
    Write-Host "[7] POST minSelect>maxSelect -> $($r7.StatusCode) UNEXPECTED"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = [System.IO.StreamReader]::new($stream)
    $body = $reader.ReadToEnd()
    Write-Host "[7] POST minSelect>maxSelect -> $code (esperado 400)"
    Write-Host "    $body"
}

Write-Host "=== CONCLUIDO ==="
