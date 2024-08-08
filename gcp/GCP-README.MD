[Back to main](../)
# Google Cloud Platform

When using GCP as cloud provider, this readme shouuld provide a basic guidance on how to setup the required resources to allow cloud-archiver to manage the backup library on this provider.

## Project setup

First you'll need a GCP Project, you can skip this part if the plan is to use an existing one.

### Create GCP project

Follow Google tutorial on how [Create a Google Cloud project](https://developers.google.com/workspace/guides/create-project) at the end of this step you should have a google project created on your google cloud console.

### Create a Bucket

A bucket is the place where all the archived files will be stored in the GCS Cloud strage, in order to create a bucket on your Google Cloud Project, you can check the [Create Buckets](https://cloud.google.com/storage/docs/creating-buckets) document for deeper understanding... and then follow this steps:

- Fill the required data, the bucket name must be unique all over the world (GCS world)
- To store your data, you can choose a specific region, I personally choosed us-central1 (iowa) as it gives 5GB monthly for free, so for dev sake I'll only use less than 5 GB
- Storage class, being the intention of this project merely a backup for your self-hosted data I'd suggest using *Standard* during some days (just in case you still moving/deleting files) class and setup a lifecycle that transforms your files after some days from *Standard* to *Archive* which is the lower cost of monthly storage (but also the highest cost and slower retrieval), but of course it's up to you based on your understanding of the different storage classes
- Public access, I personally prevent it as it is my personal data.

### Create Service Account (IAM) and Keys

You'll need a service account with at least *Editor* role in order to generate the credentials file that the application will use to connect to your GCS Cloud Storage, here is the Google tutorial on how to [Create service accounts](https://cloud.google.com/iam/docs/service-accounts-create#creating), once you have the account with *Editor* role

In order to creat the keys you can follow the [Create service account key](https://cloud.google.com/iam/docs/keys-create-delete#creating) tutorial, which should end up with you getting a key file downloaded to your computer, we'll refer to this one as the *Credentials file* on this manual.

[Back to main](../)