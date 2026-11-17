import {
    List,
    Datagrid,
    TextField,
    NumberField,
    DateField,
    SelectInput,
    useRecordContext,
} from 'react-admin';
import { Chip, Rating } from '@mui/material';
import { OrderStatus, OrderStatusColors } from '../../constants/OrderStatus.jsx';

const orderFilters = [
    <SelectInput
        key="status"
        source="status"
        label="Status"
        alwaysOn
        choices={Object.values(OrderStatus).map((status) => ({ id: status, name: status }))}
    />,
];

const OrderStatusField = () => {
    const record = useRecordContext();
    if (!record) return null;
    return <Chip label={record.status} color={OrderStatusColors[record.status] ?? 'default'} size="small" />;
};

// TEST-11 (R23): small star rating next to each rated order.
const OrderRatingField = () => {
    const record = useRecordContext();
    if (!record?.review) return null;
    return (
        <Rating
            value={record.review.rating}
            max={5}
            size="small"
            readOnly
            aria-label={`${record.review.rating} out of 5 stars`}
        />
    );
};

const OrderList = () => (
    <List filters={orderFilters} sort={{ field: 'createdAt', order: 'DESC' }}>
        <Datagrid rowClick="show">
            <TextField source="number" label="Number" />
            <TextField source="chefName" label="Chef" />
            <TextField source="receiverName" label="Receiver" />
            <NumberField source="totalPrice" label="Total" options={{ style: 'currency', currency: 'AMD' }} />
            <OrderStatusField source="status" label="Status" />
            <OrderRatingField label="Rating" sortable={false} />
            <DateField source="createdAt" label="Created" showTime />
        </Datagrid>
    </List>
);

export default OrderList;
