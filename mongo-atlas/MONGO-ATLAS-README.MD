[Back to main](../)
# Mongo Atlas

By the time of creating this project and this document, MongoDB Atlas offered 500MB free forever, if you're considering to use MongoDB Atlas make sure to check the free quota or pricing (if you're willing to pay for their service), this project uses small metadata per indexed file (yet it uses it).

## Step 1: Create an Account

1. Visit the [MongoDB Atlas website](https://www.mongodb.com/cloud/atlas) and click **Sign In** or **Start Free**.
2. Fill in the required information to create your account. You can use your email or sign up with Google.
3. Once registered, log in to your MongoDB Atlas account.

## Step 2: Create a Database Cluster

1. After logging in, click on **Build a Cluster**.
2. Select the desired **Cloud Provider** and **Region** for your cluster.
3. Choose a **Cluster Tier** (e.g., M0 Free Tier for free usage).
4. Click **Create Cluster**. This process may take a few minutes.
5. Once the cluster is created, click on the **Collections** tab to create your database.

## Step 3: Create a Database and Collection

1. In the **Collections** tab, click on **Add My Own Data**.
2. Enter the **Database Name** and for **Collection Name** use: **file_catalog**, the **Database Name** is what you'll need to provide to the docker image in the ENV variable as **SPRING_DATA_MONGODB_DATABASE**
3. Click **Create** to create your database and collection.
4. Add a new **Collection** to the Database by clicking the add Icon (+) when hovering the database name.
5. For **Collection Name** use: **sync_summary**.

## Step 4: Get Connection String for the Database

1. Go back to the **Clusters** view.
2. Click on the **Connect** button for your cluster.
3. In the pop-up window, choose your connection method. For this guide, we'll use **Connect Your Application**.
4. Select your application type (e.g., Node.js) and version.
5. Copy the provided connection string. It will look something like this:
   ```plaintext
   mongodb+srv://<username>:<password>@cluster0.mongodb.net/<dbname>?retryWrites=true&w=majority
6. This connection string is what you'll need to provide to the docker ENV variables as **SPRING_DATA_MONGODB_URI**

[Back to main](../)