import { useMemo, useState } from 'react';
import { useQueries } from '@tanstack/react-query';
import { useAuth } from '../context/AuthContext';
import { getUserTransactions } from '../services/reservationService';
import { getUserRefunds } from '../services/holidayService';
import Card from '../components/Card';
import Table from '../components/Table';
import { CreditCard, RefreshCcw, AlertTriangle, DollarSign } from 'lucide-react';

const money = (value) => `${Number(value ?? 0).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} TL`;

const MyPayments = () => {
    const { user } = useAuth();
    const [activeTab, setActiveTab] = useState('payments');
    const [transactionsQuery, refundsQuery] = useQueries({
        queries: [
            { queryKey: ['transactions', 'user', user?.id], queryFn: () => getUserTransactions(user.id), enabled: !!user?.id },
            { queryKey: ['refunds', 'user', user?.id], queryFn: () => getUserRefunds(user.id), enabled: !!user?.id },
        ],
    });

    const transactions = transactionsQuery.data ?? [];
    const refunds = useMemo(() => refundsQuery.data ?? [], [refundsQuery.data]);
    const loading = transactionsQuery.isLoading || refundsQuery.isLoading;

    const paymentColumns = [
        { field: 'yil', header: 'Yıl' },
        { field: 'ay', header: 'Ay', render: (row) => new Date(row.yil, row.ay - 1, 1).toLocaleDateString('tr-TR', { month: 'long' }) },
        { field: 'islemTarihi', header: 'İşlem Tarihi', render: (row) => new Date(row.islemTarihi).toLocaleString('tr-TR') },
        { field: 'islemGunSayisi', header: 'Gün Sayısı Farkı', render: (row) => Number(row.islemGunSayisi) < 0 || row.islemTipi === 'IPTAL' || row.islemTipi === 'İPTAL' ? `-${Math.abs(row.islemGunSayisi)} Gün` : `+${row.islemGunSayisi} Gün` },
        { field: 'islemTutari', header: 'Tutar', render: (row) => Number(row.islemGunSayisi) < 0 || row.islemTipi === 'IPTAL' || row.islemTipi === 'İPTAL' ? `-${money(row.islemTutari)}` : `+${money(row.islemTutari)}` },
        { field: 'islemTipi', header: 'İşlem Tipi', render: (row) => <span style={{ padding: '0.25rem 0.5rem', background: '#F3F4F6', color: '#4B5563', borderRadius: '4px', fontSize: '0.85rem', fontWeight: '600' }}>{row.islemTipi}</span> }
    ];

    const refundColumns = [
        { field: 'createdAt', header: 'İade Oluşturma', render: (row) => new Date(row.createdAt ?? row.islemTarihi).toLocaleString('tr-TR') },
        { field: 'refundDay', header: 'İade Günü', render: (row) => new Date(row.refundDay ?? row.tatilTarihi).toLocaleDateString('tr-TR') },
        { field: 'reason', header: 'Neden', render: (row) => row.reason === 'USER_CANCELLED' ? 'Kullanıcı iptali' : 'Tatil/resmi gün' },
        { field: 'amount', header: 'Tutar', render: (row) => <span style={{ color: '#10B981', fontWeight: '700', fontSize: '1rem' }}>{money(row.amount ?? row.iadeEdilen)}</span> },
        { field: 'status', header: 'Durum', render: (row) => row.status === 'PAID' || row.isRefunded ? <span style={{ padding: '0.25rem 0.5rem', background: '#D1FAE5', color: '#065F46', borderRadius: '4px', fontSize: '0.85rem', fontWeight: '600' }}>İADE ÖDENDİ</span> : <span style={{ padding: '0.25rem 0.5rem', background: '#FEF3C7', color: '#92400E', borderRadius: '4px', fontSize: '0.85rem', fontWeight: '600' }}>İADE BEKLİYOR</span> }
    ];

    const totalPaid = transactions.reduce((sum, r) => {
        const type = r.islemTipi;
        if (type === 'IPTAL' || type === 'İPTAL' || Number(r.islemGunSayisi) < 0) return sum;
        return sum + Number(r.islemTutari ?? 0);
    }, 0);
    const totalRefunded = refunds.filter(r => r.status === 'PAID' || r.isRefunded).reduce((sum, r) => sum + Number(r.amount ?? r.iadeEdilen ?? 0), 0);
    const pendingRefunded = refunds.filter(r => r.status === 'PENDING' || !r.isRefunded).reduce((sum, r) => sum + Number(r.amount ?? r.iadeEdilen ?? 0), 0);

    const tabStyle = (tab) => ({
        padding: '0.65rem 1.5rem',
        border: 'none',
        borderBottom: activeTab === tab ? '3px solid var(--primary)' : '3px solid transparent',
        background: 'transparent',
        color: activeTab === tab ? 'var(--primary)' : 'var(--text-muted)',
        fontWeight: activeTab === tab ? '700' : '500',
        cursor: 'pointer',
        fontSize: '1rem',
        transition: 'all 0.2s',
        display: 'flex',
        alignItems: 'center',
        gap: '0.4rem'
    });

    return (
        <div className="fade-in">
            <h1 className="page-title">Ödeme Geçmişim</h1>

            <div className="grid-3" style={{ marginBottom: '1.5rem' }}>
                <Card><div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}><div style={{ background: '#EEF2FF', padding: '1rem', borderRadius: '50%', color: 'var(--primary)' }}><CreditCard size={24} /></div><div><div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Toplam Ödeme</div><div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>{money(totalPaid)}</div></div></div></Card>
                <Card><div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}><div style={{ background: '#FEF3C7', padding: '1rem', borderRadius: '50%', color: '#92400E' }}><RefreshCcw size={24} /></div><div><div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Toplam İade</div><div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: '#10B981' }}>+ {money(totalRefunded)}</div></div></div></Card>
                <Card><div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}><div style={{ background: '#D1FAE5', padding: '1rem', borderRadius: '50%', color: '#059669' }}><DollarSign size={24} /></div><div><div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Net Tutar</div><div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: '#059669' }}>{money(totalPaid - totalRefunded)}</div></div></div></Card>
            </div>

            {pendingRefunded > 0 && <div style={{ background: 'linear-gradient(135deg, #FEF3C7 0%, #FDE68A 100%)', border: '1px solid #F59E0B', borderRadius: '12px', padding: '1rem 1.5rem', marginBottom: '1.5rem', display: 'flex', alignItems: 'center', gap: '0.75rem', color: '#92400E' }}><AlertTriangle size={20} color="#D97706" /><div><strong>Bekleyen İadeleriniz Var:</strong> Henüz ödenmemiş <strong>{money(pendingRefunded)}</strong> iade tutarınız bulunmaktadır. Statü admin ödeme işleminden sonra güncellenir.</div></div>}

            <Card>
                <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', marginBottom: '1.5rem' }}>
                    <button style={tabStyle('payments')} onClick={() => setActiveTab('payments')}><CreditCard size={18} /> Ödemelerim ({transactions.length})</button>
                    <button style={tabStyle('refunds')} onClick={() => setActiveTab('refunds')}><RefreshCcw size={18} /> Bekleyen/Alınan İadeler ({refunds.length}){refunds.filter(r => r.status === 'PENDING' || !r.isRefunded).length > 0 && <span style={{ background: '#EF4444', color: 'white', borderRadius: '50%', width: '18px', height: '18px', fontSize: '0.7rem', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' }}>{refunds.filter(r => r.status === 'PENDING' || !r.isRefunded).length}</span>}</button>
                </div>

                {transactionsQuery.isError || refundsQuery.isError ? <div className="text-danger">Ödeme ve iade verileri yüklenirken hata oluştu.</div> : loading ? <p>Yükleniyor...</p> : activeTab === 'payments' ? <Table columns={paymentColumns} data={transactions} /> : refunds.length === 0 ? <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}><RefreshCcw size={40} style={{ marginBottom: '1rem', opacity: 0.3 }} /><p>Henüz iptal veya iade bulunmamaktadır.</p></div> : <Table columns={refundColumns} data={refunds} />}
            </Card>
        </div>
    );
};

export default MyPayments;
