'use client';

import React, { useState } from 'react';
import { useOrders } from '@/lib/orders/OrdersContext';
import OpenOrdersDrawer from './OpenOrdersDrawer';
import NewOrderDialog from './NewOrderDialog';

export default function OpenOrdersIndicator() {
  const { openOrders } = useOrders();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [newOrderDialogOpen, setNewOrderDialogOpen] = useState(false);

  const count = openOrders.length;

  const handleOpenNewOrderDialog = () => {
    setNewOrderDialogOpen(true);
    setDrawerOpen(false); // Close drawer when opening modal
  };

  const handleCloseNewOrderDialog = () => {
    setNewOrderDialogOpen(false);
  };

  return (
    <>
      <button
        onClick={() => setDrawerOpen(true)}
        className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-sm font-medium transition ${
          count > 0
            ? 'bg-blue-100 text-blue-700 hover:bg-blue-200'
            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
        }`}
      >
        {count === 0 ? (
          <>
            <span>Sem comandas</span>
          </>
        ) : (
          <>
            <span>{count}</span>
            <span>{count === 1 ? 'comanda' : 'comandas'} abertas</span>
          </>
        )}
      </button>

      <OpenOrdersDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onNewOrder={handleOpenNewOrderDialog}
      />

      <NewOrderDialog open={newOrderDialogOpen} onClose={handleCloseNewOrderDialog} />
    </>
  );
}
