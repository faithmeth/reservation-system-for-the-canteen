import api from './api';

export const getAllHolidays = async () => {
    const response = await api.get('/holidays');
    return response.data;
};

export const createHoliday = async (data) => {
    const response = await api.post('/holidays', data);
    return response.data;
};

export const deleteHoliday = async (id) => {
    const response = await api.delete(`/holidays/${id}`);
    return response.data;
};

export const getAllRefunds = async () => {
    const response = await api.get('/holidays/refunds');
    return response.data;
};

export const getUserRefunds = async (userId) => {
    const response = await api.get(`/holidays/refunds/user/${userId}`);
    return response.data;
};

export const markRefunded = async (id) => {
    const response = await api.put(`/holidays/refunds/${id}/mark-refunded`);
    return response.data;
};

export const markRefundsPaid = async (ids) => {
    const response = await api.put('/holidays/refunds/mark-paid', { ids });
    return response.data;
};

export const markAllRefundsPaid = async () => {
    const response = await api.put('/holidays/refunds/mark-all-paid');
    return response.data;
};
