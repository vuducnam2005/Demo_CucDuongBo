import React from 'react';
import { Skeleton, Card, Row, Col, Space } from 'antd';

interface TableSkeletonProps {
  rows?: number;
  columns?: number;
}

export const TableSkeleton: React.FC<TableSkeletonProps> = ({ rows = 5, columns = 5 }) => {
  return (
    <div style={{ padding: '16px 0' }}>
      {/* Table Header shimmer */}
      <div
        style={{
          display: 'flex',
          gap: 16,
          padding: '12px 16px',
          background: '#fafafa',
          borderRadius: '6px 6px 0 0',
          marginBottom: 8,
        }}
      >
        {Array.from({ length: columns }).map((_, idx) => (
          <Skeleton.Input
            key={`header-${idx}`}
            active
            size="small"
            style={{ width: `${100 / columns}%`, minWidth: 80 }}
          />
        ))}
      </div>

      {/* Table Rows shimmer */}
      {Array.from({ length: rows }).map((_, rIdx) => (
        <div
          key={`row-${rIdx}`}
          style={{
            display: 'flex',
            gap: 16,
            padding: '14px 16px',
            borderBottom: '1px solid #f0f0f0',
            alignItems: 'center',
          }}
        >
          {Array.from({ length: columns }).map((_, cIdx) => (
            <Skeleton.Input
              key={`cell-${rIdx}-${cIdx}`}
              active
              size="small"
              style={{
                width: cIdx === 0 ? '60%' : '90%',
                maxWidth: 200,
              }}
            />
          ))}
        </div>
      ))}
    </div>
  );
};

interface CardSkeletonProps {
  count?: number;
}

export const CardSkeleton: React.FC<CardSkeletonProps> = ({ count = 4 }) => {
  return (
    <Row gutter={[16, 16]}>
      {Array.from({ length: count }).map((_, idx) => (
        <Col xs={24} sm={12} md={24 / count || 6} key={`card-skel-${idx}`}>
          <Card variant="borderless" style={{ borderRadius: 8 }}>
            <Skeleton active avatar={false} paragraph={{ rows: 2 }} />
          </Card>
        </Col>
      ))}
    </Row>
  );
};

export const DetailSkeleton: React.FC = () => {
  return (
    <Card variant="borderless" style={{ borderRadius: 8 }}>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Skeleton active title={{ width: '30%' }} paragraph={{ rows: 1 }} />
        <Row gutter={[24, 24]}>
          <Col span={12}>
            <Skeleton.Input active style={{ width: '100%', height: 40 }} />
          </Col>
          <Col span={12}>
            <Skeleton.Input active style={{ width: '100%', height: 40 }} />
          </Col>
          <Col span={24}>
            <Skeleton active paragraph={{ rows: 4 }} />
          </Col>
        </Row>
      </Space>
    </Card>
  );
};

export const PageSkeleton: React.FC = () => {
  return (
    <div style={{ padding: 24 }}>
      <Skeleton.Button active style={{ width: 240, height: 32, marginBottom: 16 }} />
      <Skeleton active paragraph={{ rows: 2 }} style={{ marginBottom: 24 }} />
      <CardSkeleton count={4} />
      <div style={{ marginTop: 24 }}>
        <TableSkeleton rows={6} columns={5} />
      </div>
    </div>
  );
};
