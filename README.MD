# Cloud archiver

This project was created not with the intention of breaking the self-hosted goal but to become partial self-hosted as I couldn't convince my wife that all our photos and videos were safe in several disks at a personal cloud (which is a cloud in the end) so she wanted to have some "big" cloud company involved...So I decided to keep paying for cloud storage but at a very low rate instead, going from $10 month to $0.5 month (aprox) for the same files, I tried to leverage on persisting some metadata to a database in order to reduce the "hits" to the cloud provider and therefore minimize cloud service usage fees.

## What it does?

- It scans the configured folders
- Identifies files that have not been uploaded to the cloud bucket and uploads them
- Identifies files that exist in the cloud bucket but also on local and have changed (file modified) and uploaads them
- When delete from cloud is enabled, deletes files from cloud bucket when file is no longer present in disk and was uploaded to the cloud bucket within the configured days *STANDARDDELETEDAYSLIMIT* or after the configured days *ARCHIVEDELETEDAYSHOLD*, this configuration must be done entirely aligned with the cloud bucket object lifecycle if any (to mitigate cloud provider fees).

# My personal use

I use this project to have a very cheap cloud backup to my [immich](https://immich.app/) library (I just backup the photos not the Postgres DB or other files...yet) as when I started this journey my lovely wife was not on board as she was always asking "why not in cloud?", "what if all your backed up disks break?"... I can't afford myself a NAS or anything better than a couple of SSDs...sooo here we are, it has worked for me as a daily running task that I scheduled on my minipc so it just pushes new files to the cloud and I get a beautiful email from [Mongo Atlas](https://account.mongodb.com/) every week, I have not needed to restore anything from could this far but I have tested my backup and files are there, so esentially I know that I pay a low fee (about $0.34 USD monthly for about 270GB by this update) for the storage of my backup but I'm also aware that when I need to restore my backup from here it will take a toll (I've estimated about $20 USD)...

So if you're considering to use this, leverage on the [GCS calculator](https://cloud.google.com/products/calculator) to have some approx values.

# Features

- Upload configured folders to the cloud provider bucket.
- [MongoDB Atlas](https://account.mongodb.com/) integration to track metadata allows for report creation.
    - They offer a 516MB always-free quota, which seems small but is sufficient for metadata. At the time of writing, with 25,683 registered files and 266GB of data, I was using only 7.67MB.
- Configurable sync between local locations and the cloud using cron expressions.
- Configurable delete of cloud items if they were deleted locally (disabled by default).

## Considerations

- I discovered that cloud providers impose "early delete" fees, so I configured settings to avoid these charges (I'll elaborate on this).
- I'm using the cloud provider for backup to benefit from low storage costs, despite the higher retrieval costs. I'm aware that if I need to retrieve all my backups, it will incur a substantial fee (approximately $20 to $25 at the time of writing). The alternative would be to opt for higher storage costs with no retrieval fees, but this doesn't suit my backup-only use case.

# Planned new features

- Full restore from cloud (Get everything based on the bucket instead of metadata)
- Partial restore from cloud (Get specific items from the bucket leveraging on the database data)

# MongoDB

Requires you to create 2 new collections:

- *file_catalog*: Where the files and metadata will be indexed
- *sync_summary*: Uploads/deletes summary grouped by day

## Mongo Atlas

MongoDB Atlas is a fully-managed cloud database that handles all the complexity of deploying, managing, and healing your deployments on the cloud service provider of your choice (AWS , Azure, and GCP). MongoDB Atlas is the best way to deploy, run, and scale MongoDB in the cloud.
![Mongo Atlas report example](./mongo-atlas/ReportExample.png)

*NOTE*: If you want to use this with your owon mongo instance this application should also work, just to be clear none of the above graphics will be available.

### Configure Mongo Atlas

- [Mongo Atlas](./mongo-atlas/MONGO-ATLAS-README.MD)


# Configure cloud provider

Current supported cloud providers and how to configure the needed objects:

- [Google Cloud Platform](./gcp/GCP-README.MD) or GCS Cloud Storage

# Docker compose

[Here's an example docker compose file](./docker/cloud-archiver.yml)

# Support this guy

Any support will be of course appreciated, I created this [buy me a coffee](https://www.buymeacoffee.com/ringuerel) Which will be used in storage time instead ;), any support messages/ideas will do as good.