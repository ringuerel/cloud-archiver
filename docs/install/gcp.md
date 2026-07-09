# GCP Setup

Cloud Archiver currently supports Google Cloud Storage as its real cloud provider.

## Recommended Bucket Model

For personal media backups:

| Setting | Recommended value | Reason |
|---------|-------------------|--------|
| Location type | Single region | Lower cost than multi-region for personal archives |
| Initial storage class | Standard | Avoids early-delete fees while files are fresh |
| Lifecycle rule | Standard to Archive after 30 days | Lowers long-term storage cost |
| Public access | Prevented | Personal backups should not be public |
| Object versioning | Off | Usually unnecessary for source-of-truth local folders |

## Setup Checklist

1. Create or choose a GCP project.
2. Enable the Cloud Storage API.
3. Create a private GCS bucket.
4. Add a lifecycle rule that moves objects from Standard to Archive after your chosen number of days.
5. Create a service account.
6. Grant the service account `Storage Object Admin` on the bucket.
7. Create and download a JSON key.
8. Mount the JSON key into the container.
9. Set `APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH` to the mounted key path.

## Cloud Archiver Configuration

```bash
APPLICATION_CLOUDPROVIDERCONFIG_TYPE=GCP
APPLICATION_CLOUDPROVIDERCONFIG_PROJECTID=my-gcp-project
APPLICATION_CLOUDPROVIDERCONFIG_BUCKETNAME=my-backup-bucket
APPLICATION_CLOUDPROVIDERCONFIG_CREDENTIALSFILEPATH=/cloud-provider/key.json
APPLICATION_CLOUDPROVIDERCONFIG_STORAGECLASS=ARCHIVE
```

`APPLICATION_CLOUDPROVIDERCONFIG_STORAGECLASS` is optional. If omitted, uploaded objects inherit the bucket default.

## Retention Alignment

If the bucket lifecycle moves objects from Standard to Archive after 30 days, a typical cleanup policy is:

```bash
APPLICATION_SCANFOLDERS_0_STANDARDDELETEDAYSLIMIT=30
APPLICATION_SCANFOLDERS_0_ARCHIVEDELETEDAYSHOLD=365
```

That means:

- A file deleted locally within the Standard window can be deleted from the cloud.
- A file deleted locally after it has moved to Archive is held until the Archive early-deletion window is over.
- The catalog keeps the item while it is being held.

See [Backup Safety](../operations/backup-safety.md) for the full deletion model.

## Official Google References

- [Create a Google Cloud project](https://developers.google.com/workspace/guides/create-project)
- [Create a bucket](https://cloud.google.com/storage/docs/creating-buckets)
- [Create a service account](https://cloud.google.com/iam/docs/service-accounts-create)
- [Create a service account key](https://cloud.google.com/iam/docs/keys-create-delete)
