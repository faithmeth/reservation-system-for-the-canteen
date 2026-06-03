import { useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { getAllReservations } from '../services/reservationService';
import { getAllRefunds } from '../services/holidayService';
import { getMenusByMonth } from '../services/menuService';
import Card from '../components/Card';
import Table from '../components/Table';
import { Users, DollarSign, RefreshCcw, AlertTriangle } from 'lucide-react';

const AdminReservations = () => {
    const [activeTab, setActiveTab] = useState('reservations');
    const [selectedReservation, setSelectedReservation] = useState(null);
    const [reservationsQuery, refundsQuery] = useQueries({
        queries: [
            { queryKey: ['reservations', 'all'], queryFn: getAllReservations },
            { queryKey: ['refunds', 'all'], queryFn: getAllRefunds },
        ],
    });

    const menusQuery = useQuery({
        queryKey: ['menus', selectedReservation?.yil, selectedReservation?.ay],
        queryFn: () => getMenusByMonth(selectedReservation.yil, selectedReservation.ay),
        enabled: !!selectedReservation,
    });

    // Sort descending by date so latest is on top
    const sortedReservations = [...(reservationsQuery.data ?? [])].sort((a, b) => new Date(b.islemTarihi) - new Date(a.islemTarihi));
    const sortedRefunds = [...(refundsQuery.data ?? [])].sort((a, b) => new Date(b.islemTarihi) - new Date(a.islemTarihi));

    const loading = reservationsQuery.isLoading || refundsQuery.isLoading;
    const menus = menusQuery.data ?? [];

    const reservations = sortedReservations;
    const refunds = sortedRefunds;

    const reservationColumns = [
        { field: 'islemTarihi', header: 'İşlem Tarihi', render: (row) => new Date(row.islemTarihi).toLocaleString('tr-TR') },
        { field: 'user', header: 'Kullanıcı', render: (row) => `${row.user.ad} ${row.user.soyad}` },
        { field: 'user', header: 'E-posta', render: (row) => row.user.email },
        { field: 'ay', header: 'Dönem', render: (row) => `${new Date(row.yil, row.ay - 1, 1).toLocaleDateString('tr-TR', { month: 'long' })} ${row.yil}` },
        { field: 'secilenGunSayisi', header: 'Seçilen Gün' },
        { field: 'toplamTutar', header: 'Tutar (TL)', render: (row) => `${row.toplamTutar} TL` },
        {
            field: 'odemeDurumu',
            header: 'Durum',
            render: (row) => (
                <span style={{ padding: '0.25rem 0.5rem', background: '#D1FAE5', color: '#065F46', borderRadius: '4px', fontSize: '0.85rem', fontWeight: '600' }}>
                    {row.odemeDurumu}
                </span>
            )
        }
    ];

    const refundColumns = [
        { field: 'islemTarihi', header: 'İade Tarihi', render: (row) => new Date(row.islemTarihi).toLocaleString('tr-TR') },
        { field: 'user', header: 'Kullanıcı', render: (row) => `${row.user.ad} ${row.user.soyad}` },
        { field: 'user', header: 'E-posta', render: (row) => row.user.email },
        { field: 'tatilTarihi', header: 'İade Günü', render: (row) => new Date(row.tatilTarihi).toLocaleDateString('tr-TR') },
        { field: 'tatilAciklama', header: 'İade Nedeni' },
        {
            field: 'iadeEdilen',
            header: 'İade Tutarı',
            render: (row) => (
                <span style={{ color: '#10B981', fontWeight: '700' }}>
                    + {row.iadeEdilen} TL
                </span>
            )
        },
        {
            field: 'status',
            header: 'Durum',
            render: (row) => (
                <span style={{ 
                    padding: '0.25rem 0.5rem', 
                    background: row.isRefunded ? '#D1FAE5' : '#FEF3C7', 
                    color: row.isRefunded ? '#065F46' : '#92400E', 
                    borderRadius: '4px', 
                    fontSize: '0.85rem', 
                    fontWeight: '600' 
                }}>
                    {row.isRefunded ? 'İADE YAPILDI' : 'İADE BEKLİYOR'}
                </span>
            )
        }
    ];

    const totalRevenue = reservations.reduce((sum, r) => sum + r.toplamTutar, 0);
    const allRefundsAmount = refunds.reduce((sum, r) => sum + r.iadeEdilen, 0);
    const grossRevenue = totalRevenue + allRefundsAmount;

    const totalActualRefunded = refunds
        .filter(r => r.isRefunded)
        .reduce((sum, r) => sum + r.iadeEdilen, 0);
        
    const netCiro = grossRevenue - totalActualRefunded;
    
    const holidayRefunds = refunds.filter(r => r.tatilAciklama !== 'Kullanıcı rezervasyon iptali');
    const totalHolidayRefunded = holidayRefunds.reduce((sum, r) => sum + r.iadeEdilen, 0);
    
    const selectedReservationRefunds = selectedReservation
        ? refunds.filter(refund => refund.user?.id === selectedReservation.user?.id)
        : [];

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
            <h1 className="page-title">Tüm Ödemeler ve Rezervasyonlar</h1>

            <div className="grid-2" style={{ marginBottom: '2rem' }}>
                <Card>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                        <div style={{ background: '#EEF2FF', padding: '1rem', borderRadius: '50%', color: 'var(--primary)' }}>
                            <Users size={24} />
                        </div>
                        <div>
                            <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Toplam Rezervasyon</div>
                            <div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>{reservations.length} İşlem</div>
                        </div>
                    </div>
                </Card>
                <Card>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
                        <div style={{ background: '#D1FAE5', padding: '1rem', borderRadius: '50%', color: '#065F46' }}>
                            <DollarSign size={24} />
                        </div>
                        <div>
                             <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Net Ciro</div>
                             <div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>{netCiro} TL</div>
                             {totalActualRefunded > 0 && (
                                 <div style={{ fontSize: '0.8rem', color: '#EF4444' }}>
                                     ({grossRevenue} TL rezervasyon - {totalActualRefunded} TL gerçekleşen iade)
                                 </div>
                             )}
                        </div>
                    </div>
                </Card>
            </div>

            {refunds.some(r => r.tatilAciklama !== 'Kullanıcı rezervasyon iptali') && (
                <div style={{
                    background: '#FEF3C7',
                    border: '1px solid #F59E0B',
                    borderRadius: '10px',
                    padding: '1rem 1.5rem',
                    marginBottom: '1.5rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.75rem',
                    color: '#92400E'
                }}>
                    <AlertTriangle size={20} />
                    <span>
                        <strong>{holidayRefunds.length} tatil iade işlemi</strong> gerçekleştirilmiştir.
                        Toplam <strong>{totalHolidayRefunded} TL</strong> tatil iadesi yapılmıştır.
                    </span>
                </div>
            )}

            <Card>
                <div style={{ display: 'flex', borderBottom: '1px solid var(--border)', marginBottom: '1.5rem' }}>
                    <button style={tabStyle('reservations')} onClick={() => setActiveTab('reservations')}>
                        <Users size={18} /> Rezervasyonlar ({reservations.length})
                    </button>
                    <button style={tabStyle('refunds')} onClick={() => setActiveTab('refunds')}>
                        <RefreshCcw size={18} /> İadeler ({refunds.length})
                    </button>
                </div>

                {reservationsQuery.isError || refundsQuery.isError ? (
                    <div className="text-danger">Rezervasyon ve iade verileri yüklenirken hata oluştu.</div>
                ) : loading ? (
                    <p>Yükleniyor...</p>
                ) : activeTab === 'reservations' ? (
                    <>
                        <Table
                            columns={reservationColumns}
                            data={reservations}
                            onRowClick={setSelectedReservation}
                            selectedRowId={selectedReservation?.id}
                        />

                        {selectedReservation && (
                            <div style={{ marginTop: '1.5rem', borderTop: '1px solid var(--border)', paddingTop: '1.5rem' }}>
                                <div style={{ display: 'flex', justifyContent: 'space-between', gap: '1rem', alignItems: 'flex-start', flexWrap: 'wrap', marginBottom: '1rem' }}>
                                    <div>
                                        <h3 style={{ margin: 0, fontSize: '1.1rem' }}>
                                            {selectedReservation.user.ad} {selectedReservation.user.soyad} - Rezervasyon Günleri
                                        </h3>
                                        <div style={{ color: 'var(--text-muted)', fontSize: '0.9rem', marginTop: '0.25rem' }}>
                                            {new Date(selectedReservation.yil, selectedReservation.ay - 1, 1).toLocaleDateString('tr-TR', { month: 'long' })} {selectedReservation.yil}
                                        </div>
                                    </div>
                                    <button className="btn btn-secondary" onClick={() => setSelectedReservation(null)}>
                                        Kapat
                                    </button>
                                </div>

                                {selectedReservation.secilenGunler?.length > 0 ? (
                                    <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '1rem' }}>
                                        {[...selectedReservation.secilenGunler].sort().map(dateStr => {
                                            const matchingMenu = menus.find(m => m.tarih === dateStr);
                                            return (
                                                <div
                                                    key={dateStr}
                                                    style={{ 
                                                        padding: '0.6rem 1rem', 
                                                        background: '#EEF2FF', 
                                                        color: 'var(--text-main)', 
                                                        borderRadius: '8px', 
                                                        fontWeight: 500,
                                                        display: 'flex',
                                                        alignItems: 'center',
                                                        gap: '1rem'
                                                    }}
                                                >
                                                    <div style={{ color: 'var(--primary)', fontWeight: 600, minWidth: '120px' }}>
                                                        {new Date(dateStr).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', weekday: 'short' })}
                                                    </div>
                                                    <div style={{ flex: 1, fontSize: '0.95rem' }}>
                                                        {matchingMenu ? matchingMenu.yemekListesi : <span style={{color: '#94a3b8', fontStyle: 'italic'}}>Menü bilgisi bulunamadı</span>}
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                ) : (
                                    <div style={{ color: 'var(--text-muted)', marginBottom: '1rem' }}>Bu rezervasyonda aktif gün bulunmuyor.</div>
                                )}

                                {selectedReservationRefunds.length > 0 && (
                                    <div>
                                        <h4 style={{ margin: '0 0 0.75rem', fontSize: '1rem' }}>Bu kullanıcıya ait iadeler</h4>
                                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.5rem' }}>
                                            {selectedReservationRefunds.map(refund => (
                                                <span
                                                    key={refund.id}
                                                    style={{ padding: '0.45rem 0.65rem', background: '#FEF3C7', color: '#92400E', borderRadius: '6px', fontWeight: 600 }}
                                                >
                                                    {new Date(refund.tatilTarihi).toLocaleDateString('tr-TR')} - {refund.iadeEdilen} TL
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </>
                ) : (
                    refunds.length === 0 ? (
                        <div style={{ textAlign: 'center', padding: '3rem', color: 'var(--text-muted)' }}>
                            <RefreshCcw size={40} style={{ marginBottom: '1rem', opacity: 0.3 }} />
                            <p>Henüz iade işlemi bulunmamaktadır.</p>
                        </div>
                    ) : (
                        <Table columns={refundColumns} data={refunds} />
                    )
                )}
            </Card>
        </div>
    );
};

export default AdminReservations;
