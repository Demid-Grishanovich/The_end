create table if not exists exports (
                                       id uuid primary key,
                                       project_id uuid not null,
                                       dataset_id uuid not null,
                                       format varchar(32) not null,
    status varchar(32) not null,
    file_path text not null,
    created_at timestamp with time zone not null default now()
    );

create index if not exists idx_exports_project on exports(project_id);
create index if not exists idx_exports_dataset on exports(dataset_id);
