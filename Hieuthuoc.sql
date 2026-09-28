-- MariaDB dump 10.19  Distrib 10.4.32-MariaDB, for Win64 (AMD64)
--
-- Host: localhost    Database: Hieuthuoc
-- ------------------------------------------------------
-- Server version	10.4.32-MariaDB

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Current Database: `Hieuthuoc`
--

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `hieuthuoc` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */;

USE `Hieuthuoc`;

--
-- Table structure for table `addresses`
--

DROP TABLE IF EXISTS `addresses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `addresses` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address_line` varchar(300) NOT NULL,
  `default_address` bit(1) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `recipient` varchar(100) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1fa36y2oqhao3wgg2rw1pi459` (`user_id`),
  CONSTRAINT `FK1fa36y2oqhao3wgg2rw1pi459` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `addresses`
--

LOCK TABLES `addresses` WRITE;
/*!40000 ALTER TABLE `addresses` DISABLE KEYS */;
INSERT INTO `addresses` VALUES (1,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội','','0912345678','Trần Văn An',4),(2,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội','','0987654321','Lê Thị Bình',5),(3,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM','','0934567890','Hoàng Minh Châu',6);
/*!40000 ALTER TABLE `addresses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `audit_logs` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `action` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(1000) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjs4iimve3y0xssbtve5ysyef0` (`user_id`),
  CONSTRAINT `FKjs4iimve3y0xssbtve5ysyef0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
INSERT INTO `audit_logs` VALUES (1,'system.seed','2026-09-28 15:54:58.000000','Khởi tạo dữ liệu mẫu',1);
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `batches`
--

DROP TABLE IF EXISTS `batches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `batches` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(50) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `exp_date` date NOT NULL,
  `import_price` bigint(20) NOT NULL,
  `lock_reason` varchar(300) DEFAULT NULL,
  `locked` bit(1) NOT NULL,
  `mfg_date` date DEFAULT NULL,
  `quantity` int(11) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `receipt_id` bigint(20) DEFAULT NULL,
  `supplier_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX3frq4bl54hk5k0pfveuspo9la` (`product_id`,`exp_date`),
  KEY `FK77du5ndaqiikmai6s8f6w4hmq` (`receipt_id`),
  KEY `FKkhukgaih29h4uw6j9w1kyt3t3` (`supplier_id`),
  CONSTRAINT `FK77du5ndaqiikmai6s8f6w4hmq` FOREIGN KEY (`receipt_id`) REFERENCES `receipts` (`id`),
  CONSTRAINT `FKjb38v1mk479a6t6ay2mewo03m` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKkhukgaih29h4uw6j9w1kyt3t3` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=53 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `batches`
--

LOCK TABLES `batches` WRITE;
/*!40000 ALTER TABLE `batches` DISABLE KEYS */;
INSERT INTO `batches` VALUES (1,'L24001A','2026-09-28 15:54:57.000000','2027-11-02',133200,NULL,'\0','2025-12-02',32,1,1,1),(2,'L25001B','2026-09-28 15:54:57.000000','2028-08-28',133200,NULL,'\0','2026-06-20',30,1,1,1),(3,'L24002X','2026-09-28 15:54:57.000000','2026-11-12',34560,NULL,'\0','2024-10-28',20,2,2,2),(4,'L25002B','2026-09-28 15:54:57.000000','2028-09-02',34560,NULL,'\0','2026-06-20',55,2,2,2),(5,'L24003A','2026-09-28 15:54:57.000000','2027-11-22',30240,NULL,'\0','2025-12-02',76,3,3,1),(6,'L25003B','2026-09-28 15:54:57.000000','2028-09-07',30240,NULL,'\0','2026-06-20',80,3,3,1),(7,'L24004A','2026-09-28 15:54:57.000000','2027-12-02',46800,NULL,'\0','2025-12-02',83,4,4,2),(8,'L25004B','2026-09-28 15:54:57.000000','2028-09-12',46800,NULL,'\0','2026-06-20',30,4,4,2),(9,'L24005A','2026-09-28 15:54:57.000000','2027-12-12',154800,NULL,'\0','2025-12-02',120,5,5,1),(10,'L25005B','2026-09-28 15:54:57.000000','2028-09-17',154800,NULL,'\0','2026-06-20',55,5,5,1),(11,'L24006A','2026-09-28 15:54:57.000000','2027-12-22',61200,NULL,'\0','2025-12-02',40,6,6,2),(12,'L25006B','2026-09-28 15:54:57.000000','2028-09-22',61200,NULL,'\0','2026-06-20',80,6,6,2),(13,'L24007A','2026-09-28 15:54:57.000000','2028-01-01',176400,NULL,'\0','2025-12-02',60,7,7,1),(14,'L25007B','2026-09-28 15:54:57.000000','2028-09-27',176400,NULL,'\0','2026-06-20',30,7,7,1),(15,'L24008A','2026-09-28 15:54:57.000000','2028-01-11',23040,NULL,'\0','2025-12-02',80,8,8,2),(16,'L25008B','2026-09-28 15:54:57.000000','2028-10-02',23040,NULL,'\0','2026-06-20',55,8,8,2),(17,'L24009A','2026-09-28 15:54:57.000000','2028-01-21',92160,NULL,'\0','2025-12-02',100,9,9,1),(18,'L25009B','2026-09-28 15:54:57.000000','2028-10-07',92160,NULL,'\0','2026-06-20',80,9,9,1),(19,'L24010A','2026-09-28 15:54:57.000000','2028-01-31',32400,NULL,'\0','2025-12-02',120,10,10,2),(20,'L25010B','2026-09-28 15:54:57.000000','2028-10-12',32400,NULL,'\0','2026-06-20',30,10,10,2),(21,'L24011A','2026-09-28 15:54:57.000000','2028-02-10',82800,NULL,'\0','2025-12-02',26,11,11,1),(22,'L25011B','2026-09-28 15:54:57.000000','2028-10-17',82800,NULL,'\0','2026-06-20',55,11,11,1),(23,'L24012A','2026-09-28 15:54:57.000000','2028-02-20',11520,NULL,'\0','2025-12-02',59,12,12,2),(24,'L25012B','2026-09-28 15:54:57.000000','2028-10-22',11520,NULL,'\0','2026-06-20',80,12,12,2),(25,'L23012Z','2026-09-28 15:54:57.000000','2026-09-18',11520,NULL,'\0','2024-07-20',12,12,12,2),(26,'L24013A','2026-09-28 15:54:57.000000','2028-03-01',20160,NULL,'\0','2025-12-02',80,13,13,1),(27,'L25013B','2026-09-28 15:54:57.000000','2028-10-27',20160,NULL,'\0','2026-06-20',30,13,13,1),(28,'L24014A','2026-09-28 15:54:57.000000','2028-03-11',118800,NULL,'\0','2025-12-02',86,14,14,2),(29,'L25014B','2026-09-28 15:54:57.000000','2028-11-01',118800,NULL,'\0','2026-06-20',55,14,14,2),(30,'L24015A','2026-09-28 15:54:57.000000','2028-03-21',90000,NULL,'\0','2025-12-02',113,15,15,1),(31,'L25015B','2026-09-28 15:54:57.000000','2028-11-06',90000,NULL,'\0','2026-06-20',80,15,15,1),(32,'L24016A','2026-09-28 15:54:57.000000','2028-03-31',64080,NULL,'\0','2025-12-02',33,16,16,2),(33,'L25016B','2026-09-28 15:54:57.000000','2028-11-11',64080,NULL,'\0','2026-06-20',30,16,16,2),(34,'L24017A','2026-09-28 15:54:57.000000','2028-04-10',25200,NULL,'\0','2025-12-02',46,17,17,1),(35,'L25017B','2026-09-28 15:54:57.000000','2028-11-16',25200,NULL,'\0','2026-06-20',55,17,17,1),(36,'L24018A','2026-09-28 15:54:57.000000','2028-04-20',79200,NULL,'\0','2025-12-02',75,18,18,2),(37,'L25018B','2026-09-28 15:54:57.000000','2028-11-21',79200,NULL,'\0','2026-06-20',80,18,18,2),(38,'L25019A','2026-09-28 15:54:57.000000','2028-02-10',284400,NULL,'\0','2026-08-09',5,19,19,1),(39,'L24020A','2026-09-28 15:54:57.000000','2028-05-10',71280,NULL,'\0','2025-12-02',107,20,20,2),(40,'L25020B','2026-09-28 15:54:57.000000','2028-12-01',71280,NULL,'\0','2026-06-20',55,20,20,2),(41,'L24021A','2026-09-28 15:54:57.000000','2028-05-20',640800,NULL,'\0','2025-12-02',31,21,21,1),(42,'L25021B','2026-09-28 15:54:57.000000','2028-12-06',640800,NULL,'\0','2026-06-20',80,21,21,1),(43,'L24022A','2026-09-28 15:54:57.000000','2028-05-30',54000,NULL,'\0','2025-12-02',56,22,22,2),(44,'L25022B','2026-09-28 15:54:57.000000','2028-12-11',54000,NULL,'\0','2026-06-20',30,22,22,2),(45,'L24023A','2026-09-28 15:54:57.000000','2028-06-09',25200,NULL,'\0','2025-12-02',66,23,23,1),(46,'L25023B','2026-09-28 15:54:57.000000','2028-12-16',25200,NULL,'\0','2026-06-20',55,23,23,1),(47,'L24024A','2026-09-28 15:54:57.000000','2028-06-19',349200,NULL,'\0','2025-12-02',88,24,24,2),(48,'L25024B','2026-09-28 15:54:57.000000','2028-12-21',349200,NULL,'\0','2026-06-20',80,24,24,2),(49,'L24025A','2026-09-28 15:54:57.000000','2028-06-29',248400,NULL,'\0','2025-12-02',110,25,25,1),(50,'L25025B','2026-09-28 15:54:57.000000','2028-12-26',248400,NULL,'\0','2026-06-20',30,25,25,1),(51,'L24026A','2026-09-28 15:54:57.000000','2028-07-09',43200,NULL,'\0','2025-12-02',40,26,26,2),(52,'L25026B','2026-09-28 15:54:57.000000','2028-12-31',43200,NULL,'\0','2026-06-20',55,26,26,2);
/*!40000 ALTER TABLE `batches` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `categories`
--

DROP TABLE IF EXISTS `categories`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `categories` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `icon` varchar(50) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `sort_order` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoul14ho7bctbefv8jywp5v3i2` (`slug`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'bi-thermometer-half','Giảm đau - Hạ sốt','giam-dau-ha-sot',1),(2,'bi-capsule','Kháng sinh - Kháng khuẩn','khang-sinh-khang-khuan',2),(3,'bi-heart-pulse','Tim mạch - Huyết áp','tim-mach-huyet-ap',3),(4,'bi-droplet-half','Tiêu hóa','tieu-hoa',4),(5,'bi-lungs','Hô hấp - Cảm cúm','ho-hap-cam-cum',5),(6,'bi-sun','Vitamin & Khoáng chất','vitamin-khoang-chat',6),(7,'bi-flower1','Thực phẩm chức năng','thuc-pham-chuc-nang',7),(8,'bi-bandaid','Dụng cụ y tế','dung-cu-y-te',8),(9,'bi-stars','Chăm sóc da','cham-soc-da',9),(10,'bi-activity','Thần kinh','than-kinh',10);
/*!40000 ALTER TABLE `categories` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `conversations`
--

DROP TABLE IF EXISTS `conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `conversations` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `closed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `customer_id` bigint(20) NOT NULL,
  `pharmacist_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaim02rk3jmh6iu2532wid9ukn` (`customer_id`),
  KEY `FKf6y0lwc2qnwgd1h1xnh97o7tj` (`pharmacist_id`),
  CONSTRAINT `FKaim02rk3jmh6iu2532wid9ukn` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKf6y0lwc2qnwgd1h1xnh97o7tj` FOREIGN KEY (`pharmacist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversations`
--

LOCK TABLES `conversations` WRITE;
/*!40000 ALTER TABLE `conversations` DISABLE KEYS */;
INSERT INTO `conversations` VALUES (1,'\0','2026-09-28 15:24:56.000000','2026-09-28 15:34:56.000000',6,2);
/*!40000 ALTER TABLE `conversations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `messages` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `body` varchar(2000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `image` varchar(200) DEFAULT NULL,
  `conversation_id` bigint(20) NOT NULL,
  `sender_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX8mkvn4w9p4rq8r7o524h8m04u` (`conversation_id`),
  KEY `FK4ui4nnwntodh6wjvck53dbk9m` (`sender_id`),
  CONSTRAINT `FK4ui4nnwntodh6wjvck53dbk9m` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKt492th6wsovh1nush5yl5jj8e` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
INSERT INTO `messages` VALUES (1,'Chào dược sĩ, tôi bị tăng huyết áp, có dùng được Decolgen khi bị cảm không ạ?','2026-09-28 15:24:56.000000',NULL,1,6),(2,'Chào anh, Decolgen có chứa phenylephrin có thể làm tăng huyết áp, anh không nên dùng. Anh có thể dùng paracetamol đơn thuần để hạ sốt, giảm đau và rửa mũi bằng nước muối sinh lý nhé.','2026-09-28 15:34:56.000000',NULL,1,2);
/*!40000 ALTER TABLE `messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `notifications` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `link` varchar(200) DEFAULT NULL,
  `message` varchar(500) NOT NULL,
  `seen` bit(1) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDXgt169u2w27kk35aybnhh4ocnj` (`user_id`),
  CONSTRAINT `FK9y21adhxn0ayjhfocscqox7bh` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES (1,'2026-09-28 15:54:58.000000','/staff/prescriptions','Đơn DHDEMORX1 có thuốc kê đơn cần duyệt','\0',2);
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_history`
--

DROP TABLE IF EXISTS `order_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `order_history` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `note` varchar(1000) DEFAULT NULL,
  `status` enum('CANCELLED','COMPLETED','CONFIRMED','PENDING','PENDING_RX','PREPARING','RETURNED','RX_REJECTED','SHIPPING') NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnw2ljd8jnpdc9y2ild52e79t2` (`order_id`),
  KEY `FK4voclnbr2965u9qn6c8pknive` (`user_id`),
  CONSTRAINT `FK4voclnbr2965u9qn6c8pknive` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKnw2ljd8jnpdc9y2ild52e79t2` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=115 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_history`
--

LOCK TABLES `order_history` WRITE;
/*!40000 ALTER TABLE `order_history` DISABLE KEYS */;
INSERT INTO `order_history` VALUES (1,'2026-08-31 09:15:56.000000','Khách đặt hàng','PENDING',1,5),(2,'2026-09-01 09:15:56.000000','Giao hàng thành công','COMPLETED',1,2),(3,'2026-08-31 12:15:56.000000','Khách đặt hàng','PENDING',2,6),(4,'2026-09-01 12:15:56.000000','Giao hàng thành công','COMPLETED',2,3),(5,'2026-09-01 09:15:56.000000','Khách đặt hàng','PENDING',3,4),(6,'2026-09-02 09:15:56.000000','Giao hàng thành công','COMPLETED',3,2),(7,'2026-09-02 09:15:56.000000','Khách đặt hàng','PENDING',4,6),(8,'2026-09-03 09:15:56.000000','Giao hàng thành công','COMPLETED',4,2),(9,'2026-09-02 12:15:56.000000','Khách đặt hàng','PENDING',5,4),(10,'2026-09-03 12:15:56.000000','Giao hàng thành công','COMPLETED',5,3),(11,'2026-09-02 15:15:56.000000','Khách đặt hàng','PENDING',6,5),(12,'2026-09-03 15:15:56.000000','Giao hàng thành công','COMPLETED',6,2),(13,'2026-09-03 09:15:56.000000','Khách đặt hàng','PENDING',7,5),(14,'2026-09-04 09:15:56.000000','Giao hàng thành công','COMPLETED',7,2),(15,'2026-09-03 12:15:56.000000','Khách đặt hàng','PENDING',8,6),(16,'2026-09-04 12:15:56.000000','Giao hàng thành công','COMPLETED',8,3),(17,'2026-09-04 09:15:56.000000','Khách đặt hàng','PENDING',9,4),(18,'2026-09-05 09:15:56.000000','Giao hàng thành công','COMPLETED',9,2),(19,'2026-09-05 09:15:56.000000','Khách đặt hàng','PENDING',10,6),(20,'2026-09-06 09:15:56.000000','Giao hàng thành công','COMPLETED',10,2),(21,'2026-09-05 12:15:56.000000','Khách đặt hàng','PENDING',11,4),(22,'2026-09-06 12:15:56.000000','Giao hàng thành công','COMPLETED',11,3),(23,'2026-09-05 15:15:56.000000','Khách đặt hàng','PENDING',12,5),(24,'2026-09-06 15:15:56.000000','Giao hàng thành công','COMPLETED',12,2),(25,'2026-09-06 09:15:56.000000','Khách đặt hàng','PENDING',13,5),(26,'2026-09-07 09:15:56.000000','Giao hàng thành công','COMPLETED',13,2),(27,'2026-09-06 12:15:56.000000','Khách đặt hàng','PENDING',14,6),(28,'2026-09-07 12:15:56.000000','Giao hàng thành công','COMPLETED',14,3),(29,'2026-09-07 09:15:56.000000','Khách đặt hàng','PENDING',15,4),(30,'2026-09-08 09:15:56.000000','Giao hàng thành công','COMPLETED',15,2),(31,'2026-09-08 09:15:56.000000','Khách đặt hàng','PENDING',16,6),(32,'2026-09-09 09:15:56.000000','Giao hàng thành công','COMPLETED',16,2),(33,'2026-09-08 12:15:56.000000','Khách đặt hàng','PENDING',17,4),(34,'2026-09-09 12:15:56.000000','Giao hàng thành công','COMPLETED',17,3),(35,'2026-09-08 15:15:56.000000','Khách đặt hàng','PENDING',18,5),(36,'2026-09-09 15:15:56.000000','Giao hàng thành công','COMPLETED',18,2),(37,'2026-09-09 09:15:56.000000','Khách đặt hàng','PENDING',19,5),(38,'2026-09-10 09:15:56.000000','Giao hàng thành công','COMPLETED',19,2),(39,'2026-09-09 12:15:56.000000','Khách đặt hàng','PENDING',20,6),(40,'2026-09-10 12:15:56.000000','Giao hàng thành công','COMPLETED',20,3),(41,'2026-09-10 09:15:56.000000','Khách đặt hàng','PENDING',21,4),(42,'2026-09-11 09:15:56.000000','Giao hàng thành công','COMPLETED',21,2),(43,'2026-09-11 09:15:56.000000','Khách đặt hàng','PENDING',22,6),(44,'2026-09-12 09:15:56.000000','Giao hàng thành công','COMPLETED',22,2),(45,'2026-09-11 12:15:56.000000','Khách đặt hàng','PENDING',23,4),(46,'2026-09-12 12:15:56.000000','Giao hàng thành công','COMPLETED',23,3),(47,'2026-09-11 15:15:56.000000','Khách đặt hàng','PENDING',24,5),(48,'2026-09-12 15:15:56.000000','Giao hàng thành công','COMPLETED',24,2),(49,'2026-09-12 09:15:56.000000','Khách đặt hàng','PENDING',25,5),(50,'2026-09-13 09:15:56.000000','Giao hàng thành công','COMPLETED',25,2),(51,'2026-09-12 12:15:56.000000','Khách đặt hàng','PENDING',26,6),(52,'2026-09-13 12:15:56.000000','Giao hàng thành công','COMPLETED',26,3),(53,'2026-09-13 09:15:56.000000','Khách đặt hàng','PENDING',27,4),(54,'2026-09-14 09:15:56.000000','Giao hàng thành công','COMPLETED',27,2),(55,'2026-09-14 09:15:56.000000','Khách đặt hàng','PENDING',28,6),(56,'2026-09-15 09:15:56.000000','Giao hàng thành công','COMPLETED',28,2),(57,'2026-09-14 12:15:56.000000','Khách đặt hàng','PENDING',29,4),(58,'2026-09-15 12:15:56.000000','Giao hàng thành công','COMPLETED',29,3),(59,'2026-09-14 15:15:56.000000','Khách đặt hàng','PENDING',30,5),(60,'2026-09-15 15:15:56.000000','Giao hàng thành công','COMPLETED',30,2),(61,'2026-09-15 09:15:56.000000','Khách đặt hàng','PENDING',31,5),(62,'2026-09-16 09:15:56.000000','Giao hàng thành công','COMPLETED',31,2),(63,'2026-09-15 12:15:56.000000','Khách đặt hàng','PENDING',32,6),(64,'2026-09-16 12:15:56.000000','Giao hàng thành công','COMPLETED',32,3),(65,'2026-09-16 09:15:56.000000','Khách đặt hàng','PENDING',33,4),(66,'2026-09-17 09:15:56.000000','Giao hàng thành công','COMPLETED',33,2),(67,'2026-09-17 09:15:56.000000','Khách đặt hàng','PENDING',34,6),(68,'2026-09-18 09:15:56.000000','Giao hàng thành công','COMPLETED',34,2),(69,'2026-09-17 12:15:56.000000','Khách đặt hàng','PENDING',35,4),(70,'2026-09-18 12:15:56.000000','Giao hàng thành công','COMPLETED',35,3),(71,'2026-09-17 15:15:56.000000','Khách đặt hàng','PENDING',36,5),(72,'2026-09-18 15:15:56.000000','Giao hàng thành công','COMPLETED',36,2),(73,'2026-09-18 09:15:56.000000','Khách đặt hàng','PENDING',37,5),(74,'2026-09-19 09:15:56.000000','Giao hàng thành công','COMPLETED',37,2),(75,'2026-09-18 12:15:56.000000','Khách đặt hàng','PENDING',38,6),(76,'2026-09-19 12:15:56.000000','Giao hàng thành công','COMPLETED',38,3),(77,'2026-09-19 09:15:56.000000','Khách đặt hàng','PENDING',39,4),(78,'2026-09-20 09:15:56.000000','Giao hàng thành công','COMPLETED',39,2),(79,'2026-09-20 09:15:56.000000','Khách đặt hàng','PENDING',40,6),(80,'2026-09-21 09:15:56.000000','Giao hàng thành công','COMPLETED',40,2),(81,'2026-09-20 12:15:56.000000','Khách đặt hàng','PENDING',41,4),(82,'2026-09-21 12:15:56.000000','Giao hàng thành công','COMPLETED',41,3),(83,'2026-09-20 15:15:56.000000','Khách đặt hàng','PENDING',42,5),(84,'2026-09-21 15:15:56.000000','Giao hàng thành công','COMPLETED',42,2),(85,'2026-09-21 09:15:56.000000','Khách đặt hàng','PENDING',43,5),(86,'2026-09-22 09:15:56.000000','Giao hàng thành công','COMPLETED',43,2),(87,'2026-09-21 12:15:56.000000','Khách đặt hàng','PENDING',44,6),(88,'2026-09-22 12:15:56.000000','Giao hàng thành công','COMPLETED',44,3),(89,'2026-09-22 09:15:56.000000','Khách đặt hàng','PENDING',45,4),(90,'2026-09-23 09:15:56.000000','Giao hàng thành công','COMPLETED',45,2),(91,'2026-09-23 09:15:56.000000','Khách đặt hàng','PENDING',46,6),(92,'2026-09-24 09:15:56.000000','Giao hàng thành công','COMPLETED',46,2),(93,'2026-09-23 12:15:56.000000','Khách đặt hàng','PENDING',47,4),(94,'2026-09-24 12:15:56.000000','Giao hàng thành công','COMPLETED',47,3),(95,'2026-09-23 15:15:56.000000','Khách đặt hàng','PENDING',48,5),(96,'2026-09-24 15:15:56.000000','Giao hàng thành công','COMPLETED',48,2),(97,'2026-09-24 09:15:56.000000','Khách đặt hàng','PENDING',49,5),(98,'2026-09-25 09:15:56.000000','Giao hàng thành công','COMPLETED',49,2),(99,'2026-09-24 12:15:56.000000','Khách đặt hàng','PENDING',50,6),(100,'2026-09-25 12:15:56.000000','Giao hàng thành công','COMPLETED',50,3),(101,'2026-09-25 09:15:56.000000','Khách đặt hàng','PENDING',51,4),(102,'2026-09-26 09:15:56.000000','Giao hàng thành công','COMPLETED',51,2),(103,'2026-09-26 09:15:56.000000','Khách đặt hàng','PENDING',52,6),(104,'2026-09-27 09:15:56.000000','Giao hàng thành công','COMPLETED',52,2),(105,'2026-09-26 12:15:56.000000','Khách đặt hàng','PENDING',53,4),(106,'2026-09-27 12:15:56.000000','Giao hàng thành công','COMPLETED',53,3),(107,'2026-09-26 15:15:56.000000','Khách đặt hàng','PENDING',54,5),(108,'2026-09-27 15:15:56.000000','Giao hàng thành công','COMPLETED',54,2),(109,'2026-09-27 09:15:56.000000','Khách đặt hàng','PENDING',55,5),(110,'2026-09-28 09:15:56.000000','Giao hàng thành công','COMPLETED',55,2),(111,'2026-09-27 12:15:56.000000','Khách đặt hàng','PENDING',56,6),(112,'2026-09-28 12:15:56.000000','Giao hàng thành công','COMPLETED',56,3),(113,'2026-09-28 13:54:56.000000','Khách đặt hàng kèm đơn thuốc','PENDING_RX',57,4),(114,'2026-09-28 14:54:56.000000','Khách đặt hàng','PENDING',58,5);
/*!40000 ALTER TABLE `order_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_item_batches`
--

DROP TABLE IF EXISTS `order_item_batches`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `order_item_batches` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `quantity` int(11) NOT NULL,
  `batch_id` bigint(20) NOT NULL,
  `order_item_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbxrt1f64flqk6w1nxew9j7d7o` (`batch_id`),
  KEY `FK86ogwbd51ut4ws2tv2kdslp7w` (`order_item_id`),
  CONSTRAINT `FK86ogwbd51ut4ws2tv2kdslp7w` FOREIGN KEY (`order_item_id`) REFERENCES `order_items` (`id`),
  CONSTRAINT `FKbxrt1f64flqk6w1nxew9j7d7o` FOREIGN KEY (`batch_id`) REFERENCES `batches` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_item_batches`
--

LOCK TABLES `order_item_batches` WRITE;
/*!40000 ALTER TABLE `order_item_batches` DISABLE KEYS */;
INSERT INTO `order_item_batches` VALUES (1,1,39,1),(2,2,47,2),(3,1,41,3),(4,2,7,4),(5,2,34,5),(6,1,28,6),(7,2,5,7),(8,1,30,8),(9,1,32,9),(10,2,39,10),(11,2,7,11),(12,1,41,12),(13,2,21,13),(14,1,1,14),(15,1,1,15),(16,2,28,16),(17,2,45,17),(18,1,49,18),(19,2,47,19),(20,1,21,20),(21,2,49,21),(22,1,34,22),(23,1,39,23),(24,2,36,24),(25,1,41,25),(26,2,45,26),(27,2,34,27),(28,1,7,28),(29,1,28,29),(30,2,43,30),(31,1,30,31),(32,2,3,32),(33,1,32,33),(34,2,28,34),(35,2,7,35),(36,1,30,36),(37,2,21,37),(38,1,39,38),(39,1,1,39),(40,2,45,40),(41,1,38,41),(42,2,47,42),(43,2,49,43),(44,1,7,44),(45,1,39,45),(46,2,21,46),(47,1,41,47),(48,2,34,48),(49,2,34,49),(50,1,45,50),(51,1,28,51),(52,2,32,52),(53,1,30,53),(54,2,41,54),(55,1,32,55),(56,2,1,56),(57,2,7,57),(58,1,3,58),(59,2,21,59),(60,1,28,60),(61,1,1,61),(62,2,39,62),(63,2,45,63),(64,1,23,64),(65,2,47,65),(66,1,36,66),(67,2,49,67),(68,1,45,68),(69,1,39,69),(70,2,47,70),(71,1,41,71),(72,2,7,72),(73,2,34,73),(74,1,28,74),(75,2,5,75),(76,1,30,76),(77,1,32,77),(78,2,39,78),(79,2,7,79),(80,1,41,80),(81,2,21,81),(82,1,1,82),(83,1,1,83),(84,2,28,84),(85,2,45,85),(86,1,49,86),(87,2,47,87),(88,1,21,88),(89,2,49,89),(90,1,34,90),(91,1,39,91),(92,2,36,92),(93,1,41,93),(94,2,45,94),(95,2,34,95),(96,1,7,96),(97,1,28,97),(98,2,43,98),(99,1,30,99),(100,2,3,100),(101,1,32,101),(102,2,28,102),(103,2,7,103),(104,1,30,104),(105,2,21,105),(106,1,39,106);
/*!40000 ALTER TABLE `order_item_batches` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `order_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `drug_type` enum('COSMETIC','DEVICE','ETC','OTC','SPECIAL','SUPPLEMENT') DEFAULT NULL,
  `price` bigint(20) NOT NULL,
  `product_name` varchar(200) NOT NULL,
  `quantity` int(11) NOT NULL,
  `unit` varchar(30) DEFAULT NULL,
  `order_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX3fea23hxar30bx7m7h8ed25n9` (`product_id`),
  KEY `FKbioxgbv59vetrxe0ejfubep1w` (`order_id`),
  CONSTRAINT `FKbioxgbv59vetrxe0ejfubep1w` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKocimc7dtr037rh4ls4l95nlfi` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `order_items`
--

LOCK TABLES `order_items` WRITE;
/*!40000 ALTER TABLE `order_items` DISABLE KEYS */;
INSERT INTO `order_items` VALUES (1,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',1,20),(2,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',1,24),(3,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',2,21),(4,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',2,4),(5,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',3,17),(6,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',4,14),(7,'OTC',42000,'Hapacol 250 bột sủi trẻ em',2,'Hộp',4,3),(8,'OTC',125000,'Decolgen ND',1,'Hộp',5,15),(9,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',6,16),(10,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',2,'Hộp',6,20),(11,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',7,4),(12,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',7,21),(13,'OTC',115000,'Smecta hương cam',2,'Hộp',8,11),(14,'OTC',185000,'Panadol Extra',1,'Hộp',8,1),(15,'OTC',185000,'Panadol Extra',1,'Hộp',9,1),(16,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',9,14),(17,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',10,23),(18,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',1,'Chai',10,25),(19,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',11,24),(20,'OTC',115000,'Smecta hương cam',1,'Hộp',11,11),(21,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',12,25),(22,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',1,'Tuýp',12,17),(23,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',13,20),(24,'SUPPLEMENT',110000,'Canxi D3 Corbiere',2,'Hộp',13,18),(25,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',14,21),(26,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',14,23),(27,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',15,17),(28,'OTC',65000,'Ibuprofen 400mg',1,'Hộp',15,4),(29,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',16,14),(30,'DEVICE',75000,'Nhiệt kế điện tử Microlife MT200',2,'Cái',16,22),(31,'OTC',125000,'Decolgen ND',1,'Hộp',17,15),(32,'OTC',48000,'Efferalgan 500mg viên sủi',2,'Hộp',17,2),(33,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',18,16),(34,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',18,14),(35,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',19,4),(36,'OTC',125000,'Decolgen ND',1,'Hộp',19,15),(37,'OTC',115000,'Smecta hương cam',2,'Hộp',20,11),(38,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',20,20),(39,'OTC',185000,'Panadol Extra',1,'Hộp',21,1),(40,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',22,23),(41,'SUPPLEMENT',395000,'Omega-3 Fish Oil 1000mg',1,'Lọ',22,19),(42,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',23,24),(43,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',24,25),(44,'OTC',65000,'Ibuprofen 400mg',1,'Hộp',24,4),(45,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',25,20),(46,'OTC',115000,'Smecta hương cam',2,'Hộp',25,11),(47,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',26,21),(48,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',26,17),(49,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',27,17),(50,'DEVICE',35000,'Khẩu trang y tế 4 lớp',1,'Hộp',27,23),(51,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',28,14),(52,'OTC',89000,'Siro ho Prospan 100ml',2,'Chai',28,16),(53,'OTC',125000,'Decolgen ND',1,'Hộp',29,15),(54,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',2,'Cái',29,21),(55,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',30,16),(56,'OTC',185000,'Panadol Extra',2,'Hộp',30,1),(57,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',31,4),(58,'OTC',48000,'Efferalgan 500mg viên sủi',1,'Hộp',31,2),(59,'OTC',115000,'Smecta hương cam',2,'Hộp',32,11),(60,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',32,14),(61,'OTC',185000,'Panadol Extra',1,'Hộp',33,1),(62,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',2,'Hộp',33,20),(63,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',34,23),(64,'OTC',16000,'Berberin 100mg',1,'Lọ',34,12),(65,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',35,24),(66,'SUPPLEMENT',110000,'Canxi D3 Corbiere',1,'Hộp',35,18),(67,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',36,25),(68,'DEVICE',35000,'Khẩu trang y tế 4 lớp',1,'Hộp',36,23),(69,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',37,20),(70,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',37,24),(71,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',38,21),(72,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',38,4),(73,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',39,17),(74,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',40,14),(75,'OTC',42000,'Hapacol 250 bột sủi trẻ em',2,'Hộp',40,3),(76,'OTC',125000,'Decolgen ND',1,'Hộp',41,15),(77,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',42,16),(78,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',2,'Hộp',42,20),(79,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',43,4),(80,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',43,21),(81,'OTC',115000,'Smecta hương cam',2,'Hộp',44,11),(82,'OTC',185000,'Panadol Extra',1,'Hộp',44,1),(83,'OTC',185000,'Panadol Extra',1,'Hộp',45,1),(84,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',45,14),(85,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',46,23),(86,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',1,'Chai',46,25),(87,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',47,24),(88,'OTC',115000,'Smecta hương cam',1,'Hộp',47,11),(89,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',48,25),(90,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',1,'Tuýp',48,17),(91,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',49,20),(92,'SUPPLEMENT',110000,'Canxi D3 Corbiere',2,'Hộp',49,18),(93,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',50,21),(94,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',50,23),(95,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',51,17),(96,'OTC',65000,'Ibuprofen 400mg',1,'Hộp',51,4),(97,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',52,14),(98,'DEVICE',75000,'Nhiệt kế điện tử Microlife MT200',2,'Cái',52,22),(99,'OTC',125000,'Decolgen ND',1,'Hộp',53,15),(100,'OTC',48000,'Efferalgan 500mg viên sủi',2,'Hộp',53,2),(101,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',54,16),(102,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',54,14),(103,'OTC',65000,'Ibuprofen 400mg',2,'Hộp',55,4),(104,'OTC',125000,'Decolgen ND',1,'Hộp',55,15),(105,'OTC',115000,'Smecta hương cam',2,'Hộp',56,11),(106,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',56,20),(107,'ETC',215000,'Augmentin 625mg',1,'Hộp',57,5),(108,'OTC',185000,'Panadol Extra',1,'Hộp',57,1),(109,'OTC',115000,'Smecta hương cam',2,'Hộp',58,11);
/*!40000 ALTER TABLE `order_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `orders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(300) DEFAULT NULL,
  `cancel_reason` varchar(500) DEFAULT NULL,
  `code` varchar(30) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `discount` bigint(20) NOT NULL,
  `needs_prescription` bit(1) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `payment_method` enum('COD','ONLINE') NOT NULL,
  `payment_status` enum('PAID','REFUNDED','UNPAID') NOT NULL,
  `phone` varchar(20) NOT NULL,
  `recipient` varchar(100) NOT NULL,
  `return_reason` varchar(1000) DEFAULT NULL,
  `return_status` enum('APPROVED','REJECTED','REQUESTED') DEFAULT NULL,
  `shipping_fee` bigint(20) NOT NULL,
  `shipping_method` enum('DELIVERY','PICKUP') NOT NULL,
  `status` enum('CANCELLED','COMPLETED','CONFIRMED','PENDING','PENDING_RX','PREPARING','RETURNED','RX_REJECTED','SHIPPING') NOT NULL,
  `subtotal` bigint(20) NOT NULL,
  `total` bigint(20) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `voucher_code` varchar(30) DEFAULT NULL,
  `handled_by_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgt3o4a5bqj59e9y6wakgk926t` (`code`),
  KEY `IDXqro50btxtakk2eg9v13c1se48` (`status`),
  KEY `IDXk8kupdtcdpqd57b6j4yq9uvdj` (`user_id`),
  KEY `FKi4h5wn8pifrgk16i5fbmbmi2m` (`handled_by_id`),
  CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKi4h5wn8pifrgk16i5fbmbmi2m` FOREIGN KEY (`handled_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928ETAK','2026-09-01 09:15:56.000000','2026-08-31 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',1069000,1069000,'2026-08-31 09:15:56.000000',NULL,2,5),(2,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928U7KP','2026-09-01 12:15:56.000000','2026-08-31 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',1020000,1020000,'2026-08-31 12:15:56.000000',NULL,3,6),(3,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH2609286W38','2026-09-02 09:15:56.000000','2026-09-01 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',70000,90000,'2026-09-01 09:15:56.000000',NULL,2,4),(4,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH2609289VUU','2026-09-03 09:15:56.000000','2026-09-02 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,20000,'DELIVERY','COMPLETED',249000,269000,'2026-09-02 09:15:56.000000',NULL,2,6),(5,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH26092863D7','2026-09-03 12:15:56.000000','2026-09-02 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',125000,145000,'2026-09-02 12:15:56.000000',NULL,3,4),(6,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928TD73','2026-09-03 15:15:56.000000','2026-09-02 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,20000,'DELIVERY','COMPLETED',287000,307000,'2026-09-02 15:15:56.000000',NULL,2,5),(7,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928JWA3','2026-09-04 09:15:56.000000','2026-09-03 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',1020000,1020000,'2026-09-03 09:15:56.000000',NULL,2,5),(8,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928JH9P','2026-09-04 12:15:56.000000','2026-09-03 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',415000,415000,'2026-09-03 12:15:56.000000',NULL,3,6),(9,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928TUHT','2026-09-05 09:15:56.000000','2026-09-04 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',515000,515000,'2026-09-04 09:15:56.000000',NULL,2,4),(10,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928Q3UH','2026-09-06 09:15:56.000000','2026-09-05 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',415000,415000,'2026-09-05 09:15:56.000000',NULL,2,6),(11,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928XN5H','2026-09-06 12:15:56.000000','2026-09-05 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',1085000,1085000,'2026-09-05 12:15:56.000000',NULL,3,4),(12,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH2609283UJZ','2026-09-06 15:15:56.000000','2026-09-05 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',725000,725000,'2026-09-05 15:15:56.000000',NULL,2,5),(13,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928HDUF','2026-09-07 09:15:56.000000','2026-09-06 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',319000,319000,'2026-09-06 09:15:56.000000',NULL,2,5),(14,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928TB5Z','2026-09-07 12:15:56.000000','2026-09-06 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',960000,960000,'2026-09-06 12:15:56.000000',NULL,3,6),(15,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928UU7Z','2026-09-08 09:15:56.000000','2026-09-07 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',135000,155000,'2026-09-07 09:15:56.000000',NULL,2,4),(16,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH2609282VXK','2026-09-09 09:15:56.000000','2026-09-08 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',315000,315000,'2026-09-08 09:15:56.000000',NULL,2,6),(17,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928RPZE','2026-09-09 12:15:56.000000','2026-09-08 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',221000,241000,'2026-09-08 12:15:56.000000',NULL,3,4),(18,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928B8ZA','2026-09-09 15:15:56.000000','2026-09-08 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',419000,419000,'2026-09-08 15:15:56.000000',NULL,2,5),(19,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928QNWG','2026-09-10 09:15:56.000000','2026-09-09 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,20000,'DELIVERY','COMPLETED',255000,275000,'2026-09-09 09:15:56.000000',NULL,2,5),(20,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928D42D','2026-09-10 12:15:56.000000','2026-09-09 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',329000,329000,'2026-09-09 12:15:56.000000',NULL,3,6),(21,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928XZBB','2026-09-11 09:15:56.000000','2026-09-10 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',185000,205000,'2026-09-10 09:15:56.000000',NULL,2,4),(22,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH2609289DWQ','2026-09-12 09:15:56.000000','2026-09-11 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',465000,465000,'2026-09-11 09:15:56.000000',NULL,2,6),(23,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928AMM5','2026-09-12 12:15:56.000000','2026-09-11 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',970000,970000,'2026-09-11 12:15:56.000000',NULL,3,4),(24,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH2609285432','2026-09-12 15:15:56.000000','2026-09-11 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',755000,755000,'2026-09-11 15:15:56.000000',NULL,2,5),(25,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH2609283CPM','2026-09-13 09:15:56.000000','2026-09-12 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',329000,329000,'2026-09-12 09:15:56.000000',NULL,2,5),(26,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928LCZB','2026-09-13 12:15:56.000000','2026-09-12 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',960000,960000,'2026-09-12 12:15:56.000000',NULL,3,6),(27,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928DWJX','2026-09-14 09:15:56.000000','2026-09-13 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',105000,125000,'2026-09-13 09:15:56.000000',NULL,2,4),(28,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928QUYT','2026-09-15 09:15:56.000000','2026-09-14 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',343000,343000,'2026-09-14 09:15:56.000000',NULL,2,6),(29,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928LBX5','2026-09-15 12:15:56.000000','2026-09-14 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',1905000,1905000,'2026-09-14 12:15:56.000000',NULL,3,4),(30,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928WTHR','2026-09-15 15:15:56.000000','2026-09-14 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',459000,459000,'2026-09-14 15:15:56.000000',NULL,2,5),(31,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928Q3ME','2026-09-16 09:15:56.000000','2026-09-15 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,20000,'DELIVERY','COMPLETED',178000,198000,'2026-09-15 09:15:56.000000',NULL,2,5),(32,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928RUVU','2026-09-16 12:15:56.000000','2026-09-15 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',395000,395000,'2026-09-15 12:15:56.000000',NULL,3,6),(33,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928LKPC','2026-09-17 09:15:56.000000','2026-09-16 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',383000,383000,'2026-09-16 09:15:56.000000',NULL,2,4),(34,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH26092857FE','2026-09-18 09:15:56.000000','2026-09-17 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,20000,'DELIVERY','COMPLETED',86000,106000,'2026-09-17 09:15:56.000000',NULL,2,6),(35,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928B54K','2026-09-18 12:15:56.000000','2026-09-17 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',1080000,1080000,'2026-09-17 12:15:56.000000',NULL,3,4),(36,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928KT38','2026-09-18 15:15:56.000000','2026-09-17 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',725000,725000,'2026-09-17 15:15:56.000000',NULL,2,5),(37,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928LDRT','2026-09-19 09:15:56.000000','2026-09-18 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',1069000,1069000,'2026-09-18 09:15:56.000000',NULL,2,5),(38,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH26092875TN','2026-09-19 12:15:56.000000','2026-09-18 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',1020000,1020000,'2026-09-18 12:15:56.000000',NULL,3,6),(39,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928HFV5','2026-09-20 09:15:56.000000','2026-09-19 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',70000,90000,'2026-09-19 09:15:56.000000',NULL,2,4),(40,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH2609289736','2026-09-21 09:15:56.000000','2026-09-20 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,20000,'DELIVERY','COMPLETED',249000,269000,'2026-09-20 09:15:56.000000',NULL,2,6),(41,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928UBZF','2026-09-21 12:15:56.000000','2026-09-20 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',125000,145000,'2026-09-20 12:15:56.000000',NULL,3,4),(42,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928XYY5','2026-09-21 15:15:56.000000','2026-09-20 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,20000,'DELIVERY','COMPLETED',287000,307000,'2026-09-20 15:15:56.000000',NULL,2,5),(43,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928NLGN','2026-09-22 09:15:56.000000','2026-09-21 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',1020000,1020000,'2026-09-21 09:15:56.000000',NULL,2,5),(44,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928P7SW','2026-09-22 12:15:56.000000','2026-09-21 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',415000,415000,'2026-09-21 12:15:56.000000',NULL,3,6),(45,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928LJQL','2026-09-23 09:15:56.000000','2026-09-22 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',515000,515000,'2026-09-22 09:15:56.000000',NULL,2,4),(46,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928SXQW','2026-09-24 09:15:56.000000','2026-09-23 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',415000,415000,'2026-09-23 09:15:56.000000',NULL,2,6),(47,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928LSPT','2026-09-24 12:15:56.000000','2026-09-23 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','COMPLETED',1085000,1085000,'2026-09-23 12:15:56.000000',NULL,3,4),(48,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928AJXJ','2026-09-24 15:15:56.000000','2026-09-23 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',725000,725000,'2026-09-23 15:15:56.000000',NULL,2,5),(49,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928S82S','2026-09-25 09:15:56.000000','2026-09-24 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',319000,319000,'2026-09-24 09:15:56.000000',NULL,2,5),(50,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928UNXM','2026-09-25 12:15:56.000000','2026-09-24 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',960000,960000,'2026-09-24 12:15:56.000000',NULL,3,6),(51,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH2609282MLF','2026-09-26 09:15:56.000000','2026-09-25 09:15:56.000000',0,'\0',NULL,'COD','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',135000,155000,'2026-09-25 09:15:56.000000',NULL,2,4),(52,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928F7FK','2026-09-27 09:15:56.000000','2026-09-26 09:15:56.000000',0,'\0',NULL,'COD','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',315000,315000,'2026-09-26 09:15:56.000000',NULL,2,6),(53,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DH260928D283','2026-09-27 12:15:56.000000','2026-09-26 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0912345678','Trần Văn An',NULL,NULL,20000,'DELIVERY','COMPLETED',221000,241000,'2026-09-26 12:15:56.000000',NULL,3,4),(54,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH260928P8Z7','2026-09-27 15:15:56.000000','2026-09-26 15:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,0,'DELIVERY','COMPLETED',419000,419000,'2026-09-26 15:15:56.000000',NULL,2,5),(55,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DH2609282MDE','2026-09-28 09:15:56.000000','2026-09-27 09:15:56.000000',0,'\0',NULL,'COD','PAID','0987654321','Lê Thị Bình',NULL,NULL,20000,'DELIVERY','COMPLETED',255000,275000,'2026-09-27 09:15:56.000000',NULL,2,5),(56,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,'DH260928SJJ3','2026-09-28 12:15:56.000000','2026-09-27 12:15:56.000000',0,'\0',NULL,'ONLINE','PAID','0934567890','Hoàng Minh Châu',NULL,NULL,0,'DELIVERY','COMPLETED',329000,329000,'2026-09-27 12:15:56.000000',NULL,3,6),(57,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,'DHDEMORX1',NULL,'2026-09-28 13:54:56.000000',0,'','Giao giờ hành chính','COD','UNPAID','0912345678','Trần Văn An',NULL,NULL,0,'DELIVERY','PENDING_RX',400000,400000,'2026-09-28 13:54:56.000000',NULL,NULL,4),(58,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,'DHDEMO002',NULL,'2026-09-28 14:54:56.000000',0,'\0',NULL,'COD','UNPAID','0987654321','Lê Thị Bình',NULL,NULL,20000,'DELIVERY','PENDING',230000,250000,'2026-09-28 14:54:56.000000',NULL,NULL,5);
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `posts`
--

DROP TABLE IF EXISTS `posts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `posts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `content` longtext NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `published` bit(1) NOT NULL,
  `slug` varchar(220) NOT NULL,
  `summary` varchar(500) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `author_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqmmso8qxjpbxwegdtp0l90390` (`slug`),
  KEY `FK6xvn0811tkyo3nfjk2xvqx6ns` (`author_id`),
  CONSTRAINT `FK6xvn0811tkyo3nfjk2xvqx6ns` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `posts`
--

LOCK TABLES `posts` WRITE;
/*!40000 ALTER TABLE `posts` DISABLE KEYS */;
INSERT INTO `posts` VALUES (1,'Paracetamol là thuốc hạ sốt được khuyến cáo phổ biến cho trẻ em. Liều thường dùng là 10-15mg/kg cân nặng mỗi lần, cách nhau 4-6 giờ, không quá 4-5 lần/ngày.\n\nChỉ nên dùng thuốc hạ sốt khi trẻ sốt từ 38,5°C trở lên. Với mức sốt thấp hơn, hãy cho trẻ mặc thoáng, uống nhiều nước và lau người bằng nước ấm.\n\nKhông phối hợp nhiều sản phẩm cùng chứa paracetamol (ví dụ thuốc cảm và thuốc hạ sốt) vì dễ gây quá liều, ảnh hưởng đến gan.\n\nĐưa trẻ đi khám ngay nếu trẻ dưới 3 tháng tuổi bị sốt, sốt cao liên tục trên 2 ngày, co giật, li bì hoặc nôn nhiều.','2026-09-25 15:54:56.000000','','cach-dung-thuoc-ha-sot-dung-cho-tre-em','Hướng dẫn cha mẹ dùng paracetamol an toàn, đúng liều theo cân nặng của trẻ.','Cách dùng thuốc hạ sốt đúng cho trẻ em',2),(2,'Kháng sinh chỉ có tác dụng với vi khuẩn, không có tác dụng với virus gây cảm cúm thông thường. Việc tự ý dùng kháng sinh khi không cần thiết vừa không hiệu quả, vừa làm tăng nguy cơ kháng thuốc.\n\nTheo quy định, kháng sinh là thuốc kê đơn - nhà thuốc chỉ được bán khi có đơn của bác sĩ. Đó cũng là lý do website yêu cầu bạn tải lên đơn thuốc khi mua các sản phẩm này.\n\nKhi được kê kháng sinh, hãy uống đủ liều, đủ thời gian, kể cả khi đã thấy đỡ. Ngưng thuốc sớm tạo điều kiện cho vi khuẩn sống sót và trở nên kháng thuốc.','2026-09-22 15:54:56.000000','','vi-sao-khong-nen-tu-y-dung-khang-sinh','Lạm dụng kháng sinh là nguyên nhân chính dẫn đến tình trạng kháng thuốc.','Vì sao không nên tự ý dùng kháng sinh?',3),(3,'Nên đo huyết áp vào cùng thời điểm mỗi ngày, tốt nhất là buổi sáng trước khi uống thuốc và buổi tối trước khi đi ngủ.\n\nTrước khi đo, ngồi nghỉ 5 phút, không hút thuốc, không uống cà phê trong vòng 30 phút. Đặt tay ngang mức tim, quấn vòng bít vừa khít cánh tay.\n\nGhi lại kết quả vào sổ theo dõi và mang theo khi đi tái khám để bác sĩ điều chỉnh thuốc phù hợp.','2026-09-19 15:54:56.000000','','theo-doi-huyet-ap-tai-nha-nhung-dieu-can-biet','Đo huyết áp đúng cách giúp kiểm soát bệnh tăng huyết áp hiệu quả hơn.','Theo dõi huyết áp tại nhà: những điều cần biết',2);
/*!40000 ALTER TABLE `posts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `prescriptions`
--

DROP TABLE IF EXISTS `prescriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `prescriptions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `clinic` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_note` varchar(500) DEFAULT NULL,
  `doctor_name` varchar(100) DEFAULT NULL,
  `image` varchar(200) NOT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `pharmacist_note` varchar(1000) DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `rx_date` date DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `pharmacist_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKamg8mabgywbd9af09hxff4jqv` (`order_id`),
  KEY `FKt0kqx3ijn833ssvb52nw4u2ul` (`pharmacist_id`),
  KEY `FK9sqwg2opdx0r4ts1vq7ei3q1c` (`user_id`),
  CONSTRAINT `FK9sqwg2opdx0r4ts1vq7ei3q1c` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKamg8mabgywbd9af09hxff4jqv` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`),
  CONSTRAINT `FKt0kqx3ijn833ssvb52nw4u2ul` FOREIGN KEY (`pharmacist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `prescriptions`
--

LOCK TABLES `prescriptions` WRITE;
/*!40000 ALTER TABLE `prescriptions` DISABLE KEYS */;
INSERT INTO `prescriptions` VALUES (1,NULL,'2026-09-28 13:54:56.000000','Đơn bác sĩ kê hôm nay',NULL,'sample-rx-1.svg',NULL,NULL,NULL,NULL,NULL,'PENDING',57,NULL,4);
/*!40000 ALTER TABLE `prescriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `products`
--

DROP TABLE IF EXISTS `products`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `products` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `active_ingredient` varchar(200) DEFAULT NULL,
  `contraindications` varchar(1000) DEFAULT NULL,
  `country` varchar(80) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `dosage_form` varchar(100) DEFAULT NULL,
  `drug_type` enum('COSMETIC','DEVICE','ETC','OTC','SPECIAL','SUPPLEMENT') NOT NULL,
  `image` varchar(300) DEFAULT NULL,
  `manufacturer` varchar(150) DEFAULT NULL,
  `max_per_order` int(11) DEFAULT NULL,
  `min_stock` int(11) NOT NULL,
  `name` varchar(200) NOT NULL,
  `old_price` bigint(20) DEFAULT NULL,
  `packaging` varchar(150) DEFAULT NULL,
  `price` bigint(20) NOT NULL,
  `registration_no` varchar(100) DEFAULT NULL,
  `side_effects` varchar(1000) DEFAULT NULL,
  `slug` varchar(220) NOT NULL,
  `strength` varchar(100) DEFAULT NULL,
  `unit` varchar(30) NOT NULL,
  `usage_instruction` varchar(2000) DEFAULT NULL,
  `category_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKostq1ec3toafnjok09y9l7dox` (`slug`),
  KEY `FKog2rp4qthbtt2lfyhfo32lsw9` (`category_id`),
  CONSTRAINT `FKog2rp4qthbtt2lfyhfo32lsw9` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `products`
--

LOCK TABLES `products` WRITE;
/*!40000 ALTER TABLE `products` DISABLE KEYS */;
INSERT INTO `products` VALUES (1,'','Paracetamol, Caffeine','Quá mẫn với paracetamol hoặc caffeine. Suy gan nặng.','Việt Nam','2026-07-30 15:54:56.000000','Giảm các cơn đau nhẹ đến vừa: đau đầu, đau nửa đầu, đau cơ, đau bụng kinh, đau họng, đau răng; hạ sốt.','Viên nén bao phim','OTC',NULL,'GSK',5,10,'Panadol Extra',199000,'Hộp 15 vỉ x 12 viên',185000,'VD-21189-14','Hiếm gặp: phát ban, buồn nôn. Dùng quá liều có thể gây tổn thương gan.','panadol-extra','500mg/65mg','Hộp','Người lớn và trẻ em trên 12 tuổi: 1-2 viên mỗi 4-6 giờ khi cần. Không quá 8 viên/24 giờ.',1),(2,'','Paracetamol','Suy gan. Quá mẫn với paracetamol. Chế độ ăn kiêng muối.','Pháp','2026-07-30 15:55:56.000000','Điều trị triệu chứng các chứng đau và/hoặc sốt như đau đầu, tình trạng như cúm, đau răng, nhức mỏi cơ.','Viên sủi','OTC',NULL,'UPSA',5,10,'Efferalgan 500mg viên sủi',NULL,'Hộp 4 vỉ x 4 viên',48000,'VN-19954-16','Phản ứng dị ứng, giảm tiểu cầu (rất hiếm).','efferalgan-500mg-vien-sui','500mg','Hộp','Hòa tan hoàn toàn viên thuốc vào cốc nước. Người lớn: 1-2 viên/lần, cách nhau ít nhất 4 giờ.',1),(3,'','Paracetamol','Quá mẫn với paracetamol. Trẻ bị suy gan, thiếu G6PD.','Việt Nam','2026-07-30 15:56:56.000000','Hạ sốt, giảm đau cho trẻ em trong các trường hợp cảm cúm, nhiễm khuẩn, mọc răng, sau tiêm chủng.','Bột sủi bọt','OTC',NULL,'DHG Pharma',5,10,'Hapacol 250 bột sủi trẻ em',NULL,'Hộp 24 gói x 1,5g',42000,'VD-20560-14','Ít gặp: ban da, buồn nôn.','hapacol-250-bot-sui-tre-em','250mg','Hộp','Trẻ 3-6 tuổi: 1 gói/lần; trẻ 7-12 tuổi: 2 gói/lần. Cách nhau 4-6 giờ, không quá 5 lần/ngày.',1),(4,'','Ibuprofen','Loét dạ dày tá tràng tiến triển, suy gan thận nặng, phụ nữ có thai 3 tháng cuối, hen do aspirin.','Việt Nam','2026-07-30 15:57:56.000000','Giảm đau, kháng viêm trong đau đầu, đau răng, đau bụng kinh, đau cơ xương khớp; hạ sốt.','Viên nén bao phim','OTC',NULL,'Stada Việt Nam',3,10,'Ibuprofen 400mg',NULL,'Hộp 10 vỉ x 10 viên',65000,'VD-24520-16','Đau thượng vị, buồn nôn, chóng mặt.','ibuprofen-400mg','400mg','Hộp','Người lớn: 1 viên x 2-3 lần/ngày, uống sau ăn.',1),(5,'','Amoxicillin, Acid clavulanic','Dị ứng nhóm beta-lactam (penicillin, cephalosporin).','Anh','2026-07-30 15:58:56.000000','Điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, đường tiết niệu, da và mô mềm do vi khuẩn nhạy cảm.','Viên nén bao phim','ETC',NULL,'GlaxoSmithKline',3,10,'Augmentin 625mg',NULL,'Hộp 2 vỉ x 7 viên',215000,'VN-20493-17','Tiêu chảy, buồn nôn, phát ban, nhiễm nấm Candida.','augmentin-625mg','500mg/125mg','Hộp','Theo chỉ định của bác sĩ. Thông thường: 1 viên x 2 lần/ngày, uống đầu bữa ăn.',2),(6,'','Amoxicillin','Dị ứng penicillin.','Việt Nam','2026-07-30 15:59:56.000000','Điều trị nhiễm khuẩn do vi khuẩn nhạy cảm với amoxicillin.','Viên nang cứng','ETC',NULL,'Domesco',3,10,'Amoxicillin 500mg Domesco',NULL,'Hộp 10 vỉ x 10 viên',85000,'VD-25013-16','Buồn nôn, tiêu chảy, ban da.','amoxicillin-500mg-domesco','500mg','Hộp','Theo chỉ định của bác sĩ.',2),(7,'','Cefuroxim','Dị ứng cephalosporin.','Anh','2026-07-30 16:00:56.000000','Kháng sinh cephalosporin thế hệ 2 điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, tiết niệu.','Viên nén bao phim','ETC',NULL,'GlaxoSmithKline',2,10,'Zinnat 500mg',NULL,'Hộp 1 vỉ x 10 viên',245000,'VN-18763-15','Tiêu chảy, đau đầu, tăng men gan thoáng qua.','zinnat-500mg','500mg','Hộp','Theo chỉ định của bác sĩ. Uống sau ăn.',2),(8,'','Amlodipin','Quá mẫn với dihydropyridin. Hạ huyết áp nặng, sốc tim.','Việt Nam','2026-07-30 16:01:56.000000','Điều trị tăng huyết áp, đau thắt ngực ổn định.','Viên nén','ETC',NULL,'Stada Việt Nam',5,10,'Amlodipin 5mg Stada',NULL,'Hộp 3 vỉ x 10 viên',32000,'VD-23417-15','Phù cổ chân, đỏ bừng mặt, đau đầu.','amlodipin-5mg-stada','5mg','Hộp','Theo chỉ định của bác sĩ. Thường 1 viên/ngày.',3),(9,'','Bisoprolol fumarat','Suy tim cấp, block nhĩ thất độ II-III, nhịp chậm, hen phế quản nặng.','Đức','2026-07-30 16:02:56.000000','Điều trị tăng huyết áp, đau thắt ngực, suy tim mạn ổn định.','Viên nén bao phim','ETC',NULL,'Merck',5,10,'Concor 5mg',NULL,'Hộp 3 vỉ x 10 viên',128000,'VN-17135-13','Mệt mỏi, chóng mặt, lạnh đầu chi.','concor-5mg','5mg','Hộp','Theo chỉ định của bác sĩ. Uống buổi sáng.',3),(10,'','Losartan kali','Phụ nữ có thai. Quá mẫn với losartan.','Việt Nam','2026-07-30 16:03:56.000000','Điều trị tăng huyết áp, bảo vệ thận ở bệnh nhân đái tháo đường type 2.','Viên nén bao phim','ETC',NULL,'Pymepharco',5,10,'Losartan 50mg',NULL,'Hộp 3 vỉ x 10 viên',45000,'VD-26814-17','Chóng mặt, tăng kali máu.','losartan-50mg','50mg','Hộp','Theo chỉ định của bác sĩ.',3),(11,'','Diosmectit','Quá mẫn với thành phần thuốc.','Pháp','2026-07-30 16:04:56.000000','Điều trị tiêu chảy cấp và mạn ở trẻ em và người lớn; giảm đau do viêm thực quản, dạ dày.','Bột pha hỗn dịch uống','OTC',NULL,'Ipsen',5,10,'Smecta hương cam',125000,'Hộp 30 gói',115000,'VN-20138-16','Táo bón (hiếm).','smecta-huong-cam','3g','Hộp','Người lớn: 3 gói/ngày, pha trong nửa cốc nước.',4),(12,'','Berberin clorid','Phụ nữ có thai.','Việt Nam','2026-07-30 16:05:56.000000','Hỗ trợ điều trị tiêu chảy, lỵ trực khuẩn, viêm ruột.','Viên nén bao đường','OTC',NULL,'Mekophar',10,10,'Berberin 100mg',NULL,'Lọ 100 viên',16000,'VD-22765-15','Táo bón nhẹ.','berberin-100mg','100mg','Lọ','Người lớn: 4-6 viên/lần x 2 lần/ngày.',4),(13,'','Omeprazol','Quá mẫn với omeprazol.','Việt Nam','2026-07-30 16:06:56.000000','Điều trị loét dạ dày tá tràng, trào ngược dạ dày thực quản.','Viên nang tan trong ruột','ETC',NULL,'DHG Pharma',5,10,'Omeprazol 20mg',NULL,'Hộp 2 vỉ x 7 viên',28000,'VD-28765-18','Đau đầu, buồn nôn, tiêu chảy.','omeprazol-20mg','20mg','Hộp','Theo chỉ định của bác sĩ. Uống trước ăn sáng 30 phút.',4),(14,'','Bacillus clausii','Quá mẫn với thành phần thuốc.','Ý','2026-07-30 16:07:56.000000','Phòng và điều trị rối loạn hệ vi khuẩn đường ruột, tiêu chảy do dùng kháng sinh.','Hỗn dịch uống','OTC',NULL,'Sanofi',5,10,'Enterogermina 2 tỷ/5ml',180000,'Hộp 20 ống x 5ml',165000,'VN-20720-17','Chưa ghi nhận.','enterogermina-2-ty-5ml','2 tỷ bào tử','Hộp','Người lớn: 2-3 ống/ngày; trẻ em: 1-2 ống/ngày.',4),(15,'','Paracetamol, Phenylephrin','Tăng huyết áp nặng, bệnh mạch vành, cường giáp.','Việt Nam','2026-07-30 16:08:56.000000','Giảm các triệu chứng cảm cúm: sốt, nhức đầu, sổ mũi, nghẹt mũi.','Viên nén','OTC',NULL,'United Pharma',3,10,'Decolgen ND',NULL,'Hộp 25 vỉ x 4 viên',125000,'VD-26017-16','Hồi hộp, mất ngủ nhẹ.','decolgen-nd','500mg/10mg','Hộp','Người lớn: 1 viên mỗi 6 giờ.',5),(16,'','Cao lá thường xuân','Không dung nạp fructose.','Đức','2026-07-30 16:09:56.000000','Điều trị viêm đường hô hấp cấp có kèm ho, ho do viêm phế quản mạn tính.','Siro','OTC',NULL,'Engelhard',5,10,'Siro ho Prospan 100ml',95000,'Chai 100ml',89000,'VN-19875-16','Rối loạn tiêu hóa nhẹ.','siro-ho-prospan-100ml','0,7g/100ml','Chai','Người lớn: 5-7,5ml x 3 lần/ngày.',5),(17,'','Acid ascorbic','Sỏi thận oxalat, thiếu G6PD.','Việt Nam','2026-07-30 16:10:56.000000','Bổ sung vitamin C, tăng cường sức đề kháng, chống oxy hóa.','Viên sủi','SUPPLEMENT',NULL,'Bidiphar',10,10,'Viên sủi Vitamin C 1000mg',42000,'Tuýp 10 viên',35000,'VD-29012-18','Dùng liều cao có thể gây tiêu chảy.','vien-sui-vitamin-c-1000mg','1000mg','Tuýp','Người lớn: 1 viên/ngày, hòa tan trong nước.',6),(18,'','Calci, Vitamin D3','Tăng canxi máu, sỏi thận.','Việt Nam','2026-07-30 16:11:56.000000','Bổ sung canxi và vitamin D3 cho trẻ em đang lớn, phụ nữ có thai, người cao tuổi.','Dung dịch uống','SUPPLEMENT',NULL,'Sanofi',5,10,'Canxi D3 Corbiere',NULL,'Hộp 30 ống x 5ml',110000,'VD-24098-16','Táo bón nhẹ.','canxi-d3-corbiere','500mg/200IU','Hộp','Uống 1-2 ống/ngày.',6),(19,'','Dầu cá (EPA, DHA)','Người đang dùng thuốc chống đông cần hỏi ý kiến bác sĩ.','Úc','2026-07-30 16:12:56.000000','Hỗ trợ tim mạch, não bộ và thị lực. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.','Viên nang mềm','SUPPLEMENT',NULL,'Blackmores',3,10,'Omega-3 Fish Oil 1000mg',450000,'Lọ 100 viên',395000,'TPCN 4512/2020','Ợ hơi mùi cá.','omega-3-fish-oil-1000mg','1000mg','Lọ','Uống 1 viên x 1-3 lần/ngày, sau bữa ăn.',7),(20,'','Cao bạch quả','Người đang dùng thuốc chống đông, phụ nữ có thai.','Việt Nam','2026-07-30 16:13:56.000000','Hỗ trợ tăng cường tuần hoàn máu não. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.','Viên nén','SUPPLEMENT',NULL,'Traphaco',5,10,'Ginkgo Biloba 120mg',NULL,'Hộp 3 vỉ x 10 viên',99000,'TPCN 3321/2021','Đau đầu nhẹ (hiếm).','ginkgo-biloba-120mg','120mg','Hộp','Uống 1 viên x 2 lần/ngày.',7),(21,'',NULL,NULL,'Nhật Bản','2026-07-30 16:14:56.000000','Máy đo huyết áp tự động bắp tay, công nghệ IntelliSense, phát hiện nhịp tim bất thường.',NULL,'DEVICE',NULL,'Omron',2,10,'Máy đo huyết áp bắp tay Omron HEM-7121',990000,'Hộp 1 máy',890000,'220001234/PCBB-HN',NULL,'may-do-huyet-ap-bap-tay-omron-hem-7121',NULL,'Cái','Quấn vòng bít ngang tim, ngồi yên 5 phút trước khi đo.',8),(22,'',NULL,NULL,'Thụy Sĩ','2026-07-30 16:15:56.000000','Nhiệt kế điện tử đo ở miệng, nách, hậu môn; cho kết quả sau 60 giây.',NULL,'DEVICE',NULL,'Microlife',5,10,'Nhiệt kế điện tử Microlife MT200',NULL,'Hộp 1 cái',75000,'220005678/PCBA-HN',NULL,'nhiet-ke-dien-tu-microlife-mt200',NULL,'Cái','Đặt đầu đo đúng vị trí đến khi có tiếng bíp.',8),(23,'',NULL,NULL,'Việt Nam','2026-07-30 16:16:56.000000','Khẩu trang y tế 4 lớp kháng khuẩn, lọc bụi.',NULL,'DEVICE',NULL,'Nam Anh',20,10,'Khẩu trang y tế 4 lớp',45000,'Hộp 50 cái',35000,'220009999/PCBA-HCM',NULL,'khau-trang-y-te-4-lop',NULL,'Hộp','Dùng 1 lần.',8),(24,'',NULL,NULL,'Pháp','2026-07-30 16:17:56.000000','Kem chống nắng phổ rộng, kiểm soát dầu, dành cho da nhạy cảm.','Kem','COSMETIC',NULL,'La Roche-Posay',3,10,'Kem chống nắng La Roche-Posay Anthelios SPF50+',530000,'Tuýp 50ml',485000,'123456/21/CBMP-QLD',NULL,'kem-chong-nang-la-roche-posay-anthelios-spf50',NULL,'Tuýp','Thoa trước khi ra nắng 20 phút, thoa lại sau mỗi 2 giờ.',9),(25,'',NULL,NULL,'Canada','2026-07-30 16:18:56.000000','Làm sạch dịu nhẹ, không gây kích ứng, phù hợp da nhạy cảm.','Sữa rửa mặt','COSMETIC',NULL,'Galderma',3,10,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',NULL,'Chai 500ml',345000,'98765/20/CBMP-QLD',NULL,'sua-rua-mat-cetaphil-gentle-skin-cleanser',NULL,'Chai','Dùng 2 lần/ngày.',9),(26,'','Diazepam','Suy hô hấp, nhược cơ, ngưng thở khi ngủ.','Hungary','2026-07-30 16:19:56.000000','Thuốc hướng thần - chỉ bán tại nhà thuốc theo đơn thuốc \"H\" của bác sĩ. Không bán online.','Viên nén','SPECIAL',NULL,'Gedeon Richter',NULL,10,'Seduxen 5mg',NULL,'Hộp 10 vỉ x 10 viên',60000,'VN-16582-13','Buồn ngủ, lệ thuộc thuốc.','seduxen-5mg','5mg','Hộp','Theo chỉ định của bác sĩ.',10);
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `receipt_items`
--

DROP TABLE IF EXISTS `receipt_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `receipt_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `batch_no` varchar(50) NOT NULL,
  `exp_date` date NOT NULL,
  `import_price` bigint(20) NOT NULL,
  `mfg_date` date DEFAULT NULL,
  `quantity` int(11) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `receipt_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKoflo2om70ovsg65ot9uqsk221` (`product_id`),
  KEY `FKmyfhuc1y0sjqe2geey8jx9nc2` (`receipt_id`),
  CONSTRAINT `FKmyfhuc1y0sjqe2geey8jx9nc2` FOREIGN KEY (`receipt_id`) REFERENCES `receipts` (`id`),
  CONSTRAINT `FKoflo2om70ovsg65ot9uqsk221` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=54 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receipt_items`
--

LOCK TABLES `receipt_items` WRITE;
/*!40000 ALTER TABLE `receipt_items` DISABLE KEYS */;
INSERT INTO `receipt_items` VALUES (1,'L24001A','2027-11-02',133200,'2025-12-02',40,1,1),(2,'L25001B','2028-08-28',133200,'2026-06-20',30,1,1),(3,'L24002X','2026-11-12',34560,'2024-10-28',25,2,2),(4,'L25002B','2028-09-02',34560,'2026-06-20',55,2,2),(5,'L24003A','2027-11-22',30240,'2025-12-02',80,3,3),(6,'L25003B','2028-09-07',30240,'2026-06-20',80,3,3),(7,'L24004A','2027-12-02',46800,'2025-12-02',100,4,4),(8,'L25004B','2028-09-12',46800,'2026-06-20',30,4,4),(9,'L24005A','2027-12-12',154800,'2025-12-02',120,5,5),(10,'L25005B','2028-09-17',154800,'2026-06-20',55,5,5),(11,'L24006A','2027-12-22',61200,'2025-12-02',40,6,6),(12,'L25006B','2028-09-22',61200,'2026-06-20',80,6,6),(13,'L24007A','2028-01-01',176400,'2025-12-02',60,7,7),(14,'L25007B','2028-09-27',176400,'2026-06-20',30,7,7),(15,'L24008A','2028-01-11',23040,'2025-12-02',80,8,8),(16,'L25008B','2028-10-02',23040,'2026-06-20',55,8,8),(17,'L24009A','2028-01-21',92160,'2025-12-02',100,9,9),(18,'L25009B','2028-10-07',92160,'2026-06-20',80,9,9),(19,'L24010A','2028-01-31',32400,'2025-12-02',120,10,10),(20,'L25010B','2028-10-12',32400,'2026-06-20',30,10,10),(21,'L24011A','2028-02-10',82800,'2025-12-02',40,11,11),(22,'L25011B','2028-10-17',82800,'2026-06-20',55,11,11),(23,'L24012A','2028-02-20',11520,'2025-12-02',60,12,12),(24,'L25012B','2028-10-22',11520,'2026-06-20',80,12,12),(25,'L23012Z','2026-09-18',11520,'2024-07-20',12,12,12),(26,'L24013A','2028-03-01',20160,'2025-12-02',80,13,13),(27,'L25013B','2028-10-27',20160,'2026-06-20',30,13,13),(28,'L24014A','2028-03-11',118800,'2025-12-02',100,14,14),(29,'L25014B','2028-11-01',118800,'2026-06-20',55,14,14),(30,'L24015A','2028-03-21',90000,'2025-12-02',120,15,15),(31,'L25015B','2028-11-06',90000,'2026-06-20',80,15,15),(32,'L24016A','2028-03-31',64080,'2025-12-02',40,16,16),(33,'L25016B','2028-11-11',64080,'2026-06-20',30,16,16),(34,'L24017A','2028-04-10',25200,'2025-12-02',60,17,17),(35,'L25017B','2028-11-16',25200,'2026-06-20',55,17,17),(36,'L24018A','2028-04-20',79200,'2025-12-02',80,18,18),(37,'L25018B','2028-11-21',79200,'2026-06-20',80,18,18),(38,'L25019A','2028-02-10',284400,'2026-08-09',6,19,19),(39,'L24020A','2028-05-10',71280,'2025-12-02',120,20,20),(40,'L25020B','2028-12-01',71280,'2026-06-20',55,20,20),(41,'L24021A','2028-05-20',640800,'2025-12-02',40,21,21),(42,'L25021B','2028-12-06',640800,'2026-06-20',80,21,21),(43,'L24022A','2028-05-30',54000,'2025-12-02',60,22,22),(44,'L25022B','2028-12-11',54000,'2026-06-20',30,22,22),(45,'L24023A','2028-06-09',25200,'2025-12-02',80,23,23),(46,'L25023B','2028-12-16',25200,'2026-06-20',55,23,23),(47,'L24024A','2028-06-19',349200,'2025-12-02',100,24,24),(48,'L25024B','2028-12-21',349200,'2026-06-20',80,24,24),(49,'L24025A','2028-06-29',248400,'2025-12-02',120,25,25),(50,'L25025B','2028-12-26',248400,'2026-06-20',30,25,25),(51,'L24026A','2028-07-09',43200,'2025-12-02',40,26,26),(52,'L25026B','2028-12-31',43200,'2026-06-20',55,26,26),(53,'L26019C','2028-09-17',285000,'2026-09-08',50,19,27);
/*!40000 ALTER TABLE `receipt_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `receipts`
--

DROP TABLE IF EXISTS `receipts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `receipts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `code` varchar(30) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  `approved_by_id` bigint(20) DEFAULT NULL,
  `created_by_id` bigint(20) DEFAULT NULL,
  `supplier_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpalhutkagd8lnbma35aqr048m` (`code`),
  KEY `FKowwsyfvx1k1wp3q8k3odg67fi` (`approved_by_id`),
  KEY `FKsn6hs4rk2wmjfowf6lairrhb8` (`created_by_id`),
  KEY `FK4ksphcrrl2epyxvdqwo0gat9d` (`supplier_id`),
  CONSTRAINT `FK4ksphcrrl2epyxvdqwo0gat9d` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `FKowwsyfvx1k1wp3q8k3odg67fi` FOREIGN KEY (`approved_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsn6hs4rk2wmjfowf6lairrhb8` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receipts`
--

LOCK TABLES `receipts` WRITE;
/*!40000 ALTER TABLE `receipts` DISABLE KEYS */;
INSERT INTO `receipts` VALUES (1,'2026-07-30 15:54:56.000000','PN-INIT-001','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(2,'2026-07-30 15:54:56.000000','PN-INIT-002','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(3,'2026-07-30 15:54:56.000000','PN-INIT-003','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(4,'2026-07-30 15:54:56.000000','PN-INIT-004','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(5,'2026-07-30 15:54:56.000000','PN-INIT-005','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(6,'2026-07-30 15:54:56.000000','PN-INIT-006','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(7,'2026-07-30 15:54:56.000000','PN-INIT-007','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(8,'2026-07-30 15:54:56.000000','PN-INIT-008','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(9,'2026-07-30 15:54:56.000000','PN-INIT-009','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(10,'2026-07-30 15:54:56.000000','PN-INIT-010','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(11,'2026-07-30 15:54:56.000000','PN-INIT-011','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(12,'2026-07-30 15:54:56.000000','PN-INIT-012','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(13,'2026-07-30 15:54:56.000000','PN-INIT-013','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(14,'2026-07-30 15:54:56.000000','PN-INIT-014','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(15,'2026-07-30 15:54:56.000000','PN-INIT-015','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(16,'2026-07-30 15:54:56.000000','PN-INIT-016','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(17,'2026-07-30 15:54:56.000000','PN-INIT-017','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(18,'2026-07-30 15:54:56.000000','PN-INIT-018','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(19,'2026-07-30 15:54:56.000000','PN-INIT-019','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(20,'2026-07-30 15:54:56.000000','PN-INIT-020','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(21,'2026-07-30 15:54:56.000000','PN-INIT-021','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(22,'2026-07-30 15:54:56.000000','PN-INIT-022','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(23,'2026-07-30 15:54:56.000000','PN-INIT-023','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(24,'2026-07-30 15:54:56.000000','PN-INIT-024','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(25,'2026-07-30 15:54:56.000000','PN-INIT-025','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1),(26,'2026-07-30 15:54:56.000000','PN-INIT-026','2026-07-30 15:54:56.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2),(27,NULL,'PN-DEMO-CHO-DUYET','2026-09-28 15:54:57.000000','Bổ sung hàng Omega-3 sắp hết','PENDING',NULL,2,2);
/*!40000 ALTER TABLE `receipts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reviews`
--

DROP TABLE IF EXISTS `reviews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `reviews` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `comment` varchar(1000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `hidden` bit(1) NOT NULL,
  `rating` int(11) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1nv3auyahyyy79hvtrcqgtfo9` (`product_id`,`user_id`),
  KEY `FKcgy7qjc1r99dp117y9en6lxye` (`user_id`),
  CONSTRAINT `FKcgy7qjc1r99dp117y9en6lxye` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpl51cejpw4gy5swfar8br9ngi` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reviews`
--

LOCK TABLES `reviews` WRITE;
/*!40000 ALTER TABLE `reviews` DISABLE KEYS */;
INSERT INTO `reviews` VALUES (1,'Thuốc giảm đau nhanh, giao hàng nhanh.','2026-09-28 15:54:58.000000','\0',5,1,5),(2,'Dùng ổn, đóng gói cẩn thận.','2026-09-28 15:54:58.000000','\0',4,11,6);
/*!40000 ALTER TABLE `reviews` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `settings`
--

DROP TABLE IF EXISTS `settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `settings` (
  `setting_key` varchar(50) NOT NULL,
  `setting_value` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `settings`
--

LOCK TABLES `settings` WRITE;
/*!40000 ALTER TABLE `settings` DISABLE KEYS */;
/*!40000 ALTER TABLE `settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_adjustments`
--

DROP TABLE IF EXISTS `stock_adjustments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `stock_adjustments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `quantity` int(11) NOT NULL,
  `reason` varchar(300) DEFAULT NULL,
  `batch_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7f6a15gyagmqmfqlf140ery7y` (`batch_id`),
  KEY `FKp03oubo6sxk9v82es72rxvvsg` (`user_id`),
  CONSTRAINT `FK7f6a15gyagmqmfqlf140ery7y` FOREIGN KEY (`batch_id`) REFERENCES `batches` (`id`),
  CONSTRAINT `FKp03oubo6sxk9v82es72rxvvsg` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_adjustments`
--

LOCK TABLES `stock_adjustments` WRITE;
/*!40000 ALTER TABLE `stock_adjustments` DISABLE KEYS */;
/*!40000 ALTER TABLE `stock_adjustments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `suppliers`
--

DROP TABLE IF EXISTS `suppliers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `suppliers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `address` varchar(300) DEFAULT NULL,
  `email` varchar(150) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suppliers`
--

LOCK TABLES `suppliers` WRITE;
/*!40000 ALTER TABLE `suppliers` DISABLE KEYS */;
INSERT INTO `suppliers` VALUES (1,'160 Tôn Đức Thắng, Hà Nội','sales@pharbaco.vn','Công ty CP Dược phẩm Trung ương 1','02438252000'),(2,'KCN Tân Tạo, TP.HCM','order@zuellig.vn','Công ty TNHH Phân phối Zuellig Pharma','02838123456');
/*!40000 ALTER TABLE `suppliers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `allergies` varchar(500) DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `chronic_conditions` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(150) NOT NULL,
  `full_name` varchar(100) NOT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `license_no` varchar(50) DEFAULT NULL,
  `locked` bit(1) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `points` int(11) NOT NULL,
  `pregnancy` bit(1) NOT NULL,
  `role` enum('ADMIN','CUSTOMER','PHARMACIST') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,NULL,NULL,NULL,'2026-06-30 15:54:56.000000','admin@hieuthuoc.vn','Quản trị viên',NULL,NULL,'\0','$2a$10$KvGkNTILmvidsNLDU.0Bb.X8kLl.mv2hpflBRB3.VDpS8NBwKbze2','0901000001',0,'\0','ADMIN'),(2,NULL,NULL,NULL,'2026-06-30 15:54:57.000000','duocsi@hieuthuoc.vn','DS. Nguyễn Thị Lan',NULL,'012345/HNO-CCHND','\0','$2a$10$yRq/gxFkoXIozB0WsnGnSu.KY/wsU9JLC5P5FEY0QfbqcURvaGsfe','0901000002',0,'\0','PHARMACIST'),(3,NULL,NULL,NULL,'2026-06-30 15:54:57.000000','duocsi2@hieuthuoc.vn','DS. Phạm Quốc Huy',NULL,'023456/HNO-CCHND','\0','$2a$10$fZqYYeXO5lOWDMPtJfIdru1f/nZkUJvPl46kbQ0c8j1WDX3RwfcjG','0901000003',0,'\0','PHARMACIST'),(4,'Dị ứng Aspirin','1990-05-12','Viêm dạ dày','2026-06-30 15:54:57.000000','khachhang@gmail.com','Trần Văn An','Nam',NULL,'\0','$2a$10$OGa14Nh7baHF8SHwbwD8ROYQhnDQaaFO.p7mLv0PlPSgXSDecFF2W','0912345678',907,'\0','CUSTOMER'),(5,NULL,NULL,NULL,'2026-06-30 15:54:57.000000','binh@gmail.com','Lê Thị Bình','Nữ',NULL,'\0','$2a$10$RJoabkTpseUK14G9OETR..nLJlXwVhVb84uewZJHCJtQDNh6Z7R12','0987654321',1061,'\0','CUSTOMER'),(6,NULL,NULL,'Tăng huyết áp','2026-06-30 15:54:57.000000','chau@gmail.com','Hoàng Minh Châu',NULL,NULL,'\0','$2a$10$XjUesRgiNCG7qDVDgctOX.vN4yc7wBLdyjA4Z6dzUbQ2.h.tZaHAK','0934567890',963,'\0','CUSTOMER');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `vouchers`
--

DROP TABLE IF EXISTS `vouchers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `vouchers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` varchar(30) NOT NULL,
  `description` varchar(300) DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `max_discount` bigint(20) DEFAULT NULL,
  `min_order` bigint(20) NOT NULL,
  `start_date` date DEFAULT NULL,
  `type` enum('FIXED','PERCENT') NOT NULL,
  `usage_limit` int(11) DEFAULT NULL,
  `used_count` int(11) NOT NULL,
  `discount_value` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK30ftp2biebbvpik8e49wlmady` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `vouchers`
--

LOCK TABLES `vouchers` WRITE;
/*!40000 ALTER TABLE `vouchers` DISABLE KEYS */;
INSERT INTO `vouchers` VALUES (1,'','WELCOME10','Giảm 10% cho đơn từ 100.000đ (tối đa 50.000đ)','2027-03-27',50000,100000,'2026-08-29','PERCENT',1000,0,10),(2,'','GIAM30K','Giảm 30.000đ cho đơn từ 300.000đ','2026-11-27',NULL,300000,'2026-09-18','FIXED',200,0,30000);
/*!40000 ALTER TABLE `vouchers` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-28 15:57:59
