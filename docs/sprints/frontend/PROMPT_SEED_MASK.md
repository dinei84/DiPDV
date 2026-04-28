# Prompt — Seed de produtos + Máscara monetária BR

Leia AGENTS.md antes de começar.
Branch: feature/frontend-pdv-main

---

## Parte 1 — Seed de produtos e categorias

Arquivo: `backend/src/main/java/com/dipdv/shared/config/DataInitializer.java`

Adicionar após o INSERT do admin dev, antes dos logs finais.
Todos os INSERTs com `ON CONFLICT DO NOTHING`.

### Categorias

UUIDs fixos para referência nos produtos:
- `11111111-1111-1111-1111-111111111001` — Lanches
- `11111111-1111-1111-1111-111111111002` — Bebidas
- `11111111-1111-1111-1111-111111111003` — Sobremesas
- `11111111-1111-1111-1111-111111111004` — Combos

```java
jdbc.execute("SET app.current_tenant = '00000000-0000-0000-0000-000000000001'");

String insertCategory =
    "INSERT INTO categories (id, tenant_id, name, position, active) " +
    "VALUES (?::uuid, ?::uuid, ?, ?, true) ON CONFLICT (id) DO NOTHING";

jdbc.update(insertCategory, "11111111-1111-1111-1111-111111111001",
    DEV_TENANT_ID.toString(), "Lanches", 1);
jdbc.update(insertCategory, "11111111-1111-1111-1111-111111111002",
    DEV_TENANT_ID.toString(), "Bebidas", 2);
jdbc.update(insertCategory, "11111111-1111-1111-1111-111111111003",
    DEV_TENANT_ID.toString(), "Sobremesas", 3);
jdbc.update(insertCategory, "11111111-1111-1111-1111-111111111004",
    DEV_TENANT_ID.toString(), "Combos", 4);
```

### Produtos

Verificar o schema de `products` no V1 para confirmar colunas.
Schema esperado: `id, tenant_id, category_id, name, price,
stock_quantity, stock_min_level, active`.

```java
String insertProduct =
    "INSERT INTO products (id, tenant_id, category_id, name, price, " +
    "stock_quantity, stock_min_level, active) " +
    "VALUES (?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, true) " +
    "ON CONFLICT (id) DO NOTHING";

// Lanches
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222001",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111001",
    "X-Burguer", 18.90, 50, 10);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222002",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111001",
    "X-Salada", 21.50, 40, 10);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222003",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111001",
    "X-Bacon", 23.00, 35, 10);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222004",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111001",
    "Cheeseburguer Duplo", 26.90, 30, 5);

// Bebidas
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222005",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111002",
    "Coca-Cola 350ml", 6.50, 100, 20);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222006",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111002",
    "Guaraná Antarctica 350ml", 6.00, 100, 20);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222007",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111002",
    "Suco de Laranja 300ml", 8.50, 60, 15);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222008",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111002",
    "Água Mineral 500ml", 4.00, 120, 30);

// Sobremesas
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222009",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111003",
    "Milk-shake Chocolate", 14.90, 25, 5);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222010",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111003",
    "Sorvete 2 bolas", 9.50, 40, 10);

// Combos
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222011",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111004",
    "Combo X-Burguer + Refri + Batata", 28.90, 20, 5);
jdbc.update(insertProduct, "22222222-2222-2222-2222-222222222012",
    DEV_TENANT_ID.toString(), "11111111-1111-1111-1111-111111111004",
    "Combo Família (4 lanches + 4 refris)", 89.90, 15, 3);

log.info("║  Produtos de teste criados: 12 itens      ║");
```

---

## Parte 2 — Máscara monetária BR

Criar utilitário reutilizável para formatação de valores monetários.

### Arquivo 1 — Utilitário

Arquivo: `frontend/src/lib/currency.ts`

```typescript
// Formata centavos para string BR: 10050 → "100,50"
export function formatCents(cents: number): string {
  const reais = Math.floor(cents / 100);
  const centavos = cents % 100;
  return `${reais.toLocaleString('pt-BR')},${String(centavos).padStart(2, '0')}`;
}

// Remove tudo que não é dígito e retorna centavos
export function parseCents(value: string): number {
  const digits = value.replace(/\D/g, '');
  return digits ? parseInt(digits, 10) : 0;
}

// Converte centavos para decimal (para enviar à API)
export function centsToDecimal(cents: number): number {
  return cents / 100;
}
```

### Arquivo 2 — Componente de input

Arquivo: `frontend/src/components/ui/MoneyInput.tsx`

```typescript
'use client';
import { useState, useEffect } from 'react';
import { formatCents, parseCents } from '@/lib/currency';

interface Props {
  value: number;           // valor em centavos
  onChange: (cents: number) => void;
  placeholder?: string;
  className?: string;
  autoFocus?: boolean;
  min?: number;
}

export default function MoneyInput({
  value,
  onChange,
  placeholder = '0,00',
  className = '',
  autoFocus,
  min,
}: Props) {
  const [display, setDisplay] = useState(formatCents(value));

  useEffect(() => {
    setDisplay(formatCents(value));
  }, [value]);

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const cents = parseCents(e.target.value);
    if (min !== undefined && cents < min) return;
    setDisplay(formatCents(cents));
    onChange(cents);
  }

  return (
    <div className="relative">
      <span className="absolute left-3 top-1/2 -translate-y-1/2
                       text-amber-400 font-mono text-sm pointer-events-none">
        R$
      </span>
      <input
        type="text"
        inputMode="numeric"
        value={display}
        onChange={handleChange}
        placeholder={placeholder}
        autoFocus={autoFocus}
        className={`w-full bg-slate-800 border border-slate-600
                    text-white font-mono text-lg rounded-lg
                    pl-12 pr-4 py-3 focus:outline-none
                    focus:border-amber-500 ${className}`}
      />
    </div>
  );
}
```

### Arquivo 3 — Atualizar CashRegisterModal

Arquivo: `frontend/src/components/pdv/CashRegisterModal.tsx`

Substituir o `<input type="number">` pelo componente `MoneyInput`:
- Estado local: `const [cents, setCents] = useState(0);`
- No submit: `onOpen(cents / 100)` para enviar em reais decimais
- Remover a spinner/setas nativas do browser

### Arquivo 4 — Atualizar OrderPanel

Arquivo: `frontend/src/components/pdv/OrderPanel.tsx`

No campo "Valor recebido" (método CASH):
- Trocar `<input type="number">` por `MoneyInput`
- Estado em centavos: `const [cashCents, setCashCents] = useState(0);`
- Comparar com `order.total * 100` para validação
- Calcular troco: `changeAmount = (cashCents / 100) - order.total`
- Desabilitar botão se `cashCents < order.total * 100`

---

## Validação

```bash
# 1. Rebuild backend com seed novo
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Aguardar logs:
```
[SEED] Iniciando inicialização de dados...
Produtos de teste criados: 12 itens
```

```bash
# 2. Frontend
cd frontend
npm run dev
```

Testar em `http://localhost:3000`:
1. Abrir caixa → máscara `R$ 0,00` sem setas ✓
2. Digitar `10000` → aparece `R$ 100,00` ✓
3. Catálogo carrega 12 produtos em 4 categorias ✓
4. Filtro por categoria funciona ✓
5. Adiciona itens à comanda ✓
6. Pagamento CASH com máscara e troco correto ✓

---

## Relatório esperado

Arquivos criados/modificados + resultado dos testes + desvios.
