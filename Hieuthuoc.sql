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
INSERT INTO `addresses` VALUES (1,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội','','0912345678','Trần Văn An',6),(2,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội','','0987654321','Lê Thị Bình',7),(3,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM','','0934567890','Hoàng Minh Châu',8);
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
INSERT INTO `audit_logs` VALUES (1,'system.seed','2026-09-28 20:18:06.000000','Khởi tạo dữ liệu mẫu',1);
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `banners`
--

DROP TABLE IF EXISTS `banners`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `banners` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `button_text` varchar(50) DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `image` varchar(300) DEFAULT NULL,
  `link` varchar(300) DEFAULT NULL,
  `sort_order` int(11) NOT NULL,
  `start_date` date DEFAULT NULL,
  `subtitle` varchar(300) DEFAULT NULL,
  `theme` varchar(20) DEFAULT NULL,
  `title` varchar(150) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `banners`
--

LOCK TABLES `banners` WRITE;
/*!40000 ALTER TABLE `banners` DISABLE KEYS */;
/*!40000 ALTER TABLE `banners` ENABLE KEYS */;
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
  `warehouse_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX3frq4bl54hk5k0pfveuspo9la` (`product_id`,`exp_date`),
  KEY `FK77du5ndaqiikmai6s8f6w4hmq` (`receipt_id`),
  KEY `FKkhukgaih29h4uw6j9w1kyt3t3` (`supplier_id`),
  KEY `FKsgp85bi3ebbvbk0agae373ltl` (`warehouse_id`),
  CONSTRAINT `FK77du5ndaqiikmai6s8f6w4hmq` FOREIGN KEY (`receipt_id`) REFERENCES `receipts` (`id`),
  CONSTRAINT `FKjb38v1mk479a6t6ay2mewo03m` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKkhukgaih29h4uw6j9w1kyt3t3` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `FKsgp85bi3ebbvbk0agae373ltl` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `batches`
--

LOCK TABLES `batches` WRITE;
/*!40000 ALTER TABLE `batches` DISABLE KEYS */;
INSERT INTO `batches` VALUES (1,'L24001A','2026-09-28 20:18:04.000000','2027-11-02',9000,NULL,'\0','2025-12-02',592,1,1,1,NULL),(2,'L25001B','2026-09-28 20:18:04.000000','2028-08-28',9000,NULL,'\0','2026-06-20',450,1,1,1,NULL),(3,'L24002X','2026-09-28 20:18:04.000000','2026-11-12',9000,NULL,'\0','2024-10-28',95,2,2,2,NULL),(4,'L25002B','2026-09-28 20:18:04.000000','2028-09-02',9000,NULL,'\0','2026-06-20',220,2,2,2,NULL),(5,'L24003A','2026-09-28 20:18:05.000000','2027-11-22',30240,NULL,'\0','2025-12-02',76,3,3,1,NULL),(6,'L25003B','2026-09-28 20:18:05.000000','2028-09-07',30240,NULL,'\0','2026-06-20',80,3,3,1,NULL),(7,'L24004A','2026-09-28 20:18:05.000000','2027-12-02',5040,NULL,'\0','2025-12-02',983,4,4,2,NULL),(8,'L25004B','2026-09-28 20:18:05.000000','2028-09-12',5040,NULL,'\0','2026-06-20',300,4,4,2,NULL),(9,'L24005A','2026-09-28 20:18:05.000000','2027-12-12',79200,NULL,'\0','2025-12-02',240,5,5,1,NULL),(10,'L25005B','2026-09-28 20:18:05.000000','2028-09-17',79200,NULL,'\0','2026-06-20',110,5,5,1,NULL),(11,'L24006A','2026-09-28 20:18:05.000000','2027-12-22',6480,NULL,'\0','2025-12-02',400,6,6,2,NULL),(12,'L25006B','2026-09-28 20:18:05.000000','2028-09-22',6480,NULL,'\0','2026-06-20',800,6,6,2,NULL),(13,'L24007A','2026-09-28 20:18:05.000000','2028-01-01',176400,NULL,'\0','2025-12-02',60,7,7,1,NULL),(14,'L25007B','2026-09-28 20:18:05.000000','2028-09-27',176400,NULL,'\0','2026-06-20',30,7,7,1,NULL),(15,'L24008A','2026-09-28 20:18:05.000000','2028-01-11',7920,NULL,'\0','2025-12-02',240,8,8,2,NULL),(16,'L25008B','2026-09-28 20:18:05.000000','2028-10-02',7920,NULL,'\0','2026-06-20',165,8,8,2,NULL),(17,'L24009A','2026-09-28 20:18:05.000000','2028-01-21',32400,NULL,'\0','2025-12-02',300,9,9,1,NULL),(18,'L25009B','2026-09-28 20:18:05.000000','2028-10-07',32400,NULL,'\0','2026-06-20',240,9,9,1,NULL),(19,'L24010A','2026-09-28 20:18:05.000000','2028-01-31',32400,NULL,'\0','2025-12-02',120,10,10,2,NULL),(20,'L25010B','2026-09-28 20:18:05.000000','2028-10-12',32400,NULL,'\0','2026-06-20',30,10,10,2,NULL),(21,'L24011A','2026-09-28 20:18:05.000000','2028-02-10',82800,NULL,'\0','2025-12-02',26,11,11,1,NULL),(22,'L25011B','2026-09-28 20:18:05.000000','2028-10-17',82800,NULL,'\0','2026-06-20',55,11,11,1,NULL),(23,'L24012A','2026-09-28 20:18:05.000000','2028-02-20',11520,NULL,'\0','2025-12-02',59,12,12,2,NULL),(24,'L25012B','2026-09-28 20:18:05.000000','2028-10-22',11520,NULL,'\0','2026-06-20',80,12,12,2,NULL),(25,'L23012Z','2026-09-28 20:18:05.000000','2026-09-18',11520,NULL,'\0','2024-07-20',12,12,12,2,NULL),(26,'L24013A','2026-09-28 20:18:05.000000','2028-03-01',20160,NULL,'\0','2025-12-02',80,13,13,1,NULL),(27,'L25013B','2026-09-28 20:18:05.000000','2028-10-27',20160,NULL,'\0','2026-06-20',30,13,13,1,NULL),(28,'L24014A','2026-09-28 20:18:05.000000','2028-03-11',118800,NULL,'\0','2025-12-02',86,14,14,2,NULL),(29,'L25014B','2026-09-28 20:18:05.000000','2028-11-01',118800,NULL,'\0','2026-06-20',55,14,14,2,NULL),(30,'L24015A','2026-09-28 20:18:05.000000','2028-03-21',3600,NULL,'\0','2025-12-02',2993,15,15,1,NULL),(31,'L25015B','2026-09-28 20:18:05.000000','2028-11-06',3600,NULL,'\0','2026-06-20',2000,15,15,1,NULL),(32,'L24016A','2026-09-28 20:18:05.000000','2028-03-31',64080,NULL,'\0','2025-12-02',33,16,16,2,NULL),(33,'L25016B','2026-09-28 20:18:05.000000','2028-11-11',64080,NULL,'\0','2026-06-20',30,16,16,2,NULL),(34,'L24017A','2026-09-28 20:18:05.000000','2028-04-10',25200,NULL,'\0','2025-12-02',46,17,17,1,NULL),(35,'L25017B','2026-09-28 20:18:05.000000','2028-11-16',25200,NULL,'\0','2026-06-20',55,17,17,1,NULL),(36,'L24018A','2026-09-28 20:18:05.000000','2028-04-20',79200,NULL,'\0','2025-12-02',75,18,18,2,NULL),(37,'L25018B','2026-09-28 20:18:05.000000','2028-11-21',79200,NULL,'\0','2026-06-20',80,18,18,2,NULL),(38,'L25019A','2026-09-28 20:18:05.000000','2028-02-10',284400,NULL,'\0','2026-08-09',5,19,19,1,NULL),(39,'L24020A','2026-09-28 20:18:05.000000','2028-05-10',71280,NULL,'\0','2025-12-02',107,20,20,2,NULL),(40,'L25020B','2026-09-28 20:18:05.000000','2028-12-01',71280,NULL,'\0','2026-06-20',55,20,20,2,NULL),(41,'L24021A','2026-09-28 20:18:05.000000','2028-05-20',640800,NULL,'\0','2025-12-02',31,21,21,1,NULL),(42,'L25021B','2026-09-28 20:18:05.000000','2028-12-06',640800,NULL,'\0','2026-06-20',80,21,21,1,NULL),(43,'L24022A','2026-09-28 20:18:05.000000','2028-05-30',54000,NULL,'\0','2025-12-02',56,22,22,2,NULL),(44,'L25022B','2026-09-28 20:18:05.000000','2028-12-11',54000,NULL,'\0','2026-06-20',30,22,22,2,NULL),(45,'L24023A','2026-09-28 20:18:05.000000','2028-06-09',25200,NULL,'\0','2025-12-02',66,23,23,1,NULL),(46,'L25023B','2026-09-28 20:18:05.000000','2028-12-16',25200,NULL,'\0','2026-06-20',55,23,23,1,NULL),(47,'L24024A','2026-09-28 20:18:05.000000','2028-06-19',349200,NULL,'\0','2025-12-02',88,24,24,2,NULL),(48,'L25024B','2026-09-28 20:18:05.000000','2028-12-21',349200,NULL,'\0','2026-06-20',80,24,24,2,NULL),(49,'L24025A','2026-09-28 20:18:05.000000','2028-06-29',248400,NULL,'\0','2025-12-02',110,25,25,1,NULL),(50,'L25025B','2026-09-28 20:18:05.000000','2028-12-26',248400,NULL,'\0','2026-06-20',30,25,25,1,NULL),(51,'L24026A','2026-09-28 20:18:05.000000','2028-07-09',43200,NULL,'\0','2025-12-02',40,26,26,2,NULL),(52,'L25026B','2026-09-28 20:18:05.000000','2028-12-31',43200,NULL,'\0','2026-06-20',55,26,26,2,NULL),(53,'DT25001','2026-09-28 20:18:06.000000','2029-03-16',8750,NULL,'\0','2026-08-29',60,1,NULL,1,2),(54,'DT25002','2026-09-28 20:18:06.000000','2029-03-16',8750,NULL,'\0','2026-08-29',60,2,NULL,1,2),(55,'DT25003','2026-09-28 20:18:06.000000','2029-03-16',29400,NULL,'\0','2026-08-29',60,3,NULL,1,2);
/*!40000 ALTER TABLE `batches` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `callback_requests`
--

DROP TABLE IF EXISTS `callback_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `callback_requests` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `done` bit(1) NOT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `phone` varchar(20) NOT NULL,
  `preferred_time` varchar(100) DEFAULT NULL,
  `result` varchar(500) DEFAULT NULL,
  `handled_by_id` bigint(20) DEFAULT NULL,
  `product_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmiflm9syrv8mygop763tep66t` (`handled_by_id`),
  KEY `FKdotdh8hwal2efik9bcpw3fjt8` (`product_id`),
  KEY `FKbwj79igr7kt8c3v99s7kvg64n` (`user_id`),
  CONSTRAINT `FKbwj79igr7kt8c3v99s7kvg64n` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKdotdh8hwal2efik9bcpw3fjt8` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKmiflm9syrv8mygop763tep66t` FOREIGN KEY (`handled_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `callback_requests`
--

LOCK TABLES `callback_requests` WRITE;
/*!40000 ALTER TABLE `callback_requests` DISABLE KEYS */;
INSERT INTO `callback_requests` VALUES (1,'2026-09-28 20:18:06.000000','\0',NULL,'Lê Thị Bình','Cần tư vấn thuốc cho bé 3 tuổi bị sốt','0987654321','Chiều (14h - 17h)',NULL,NULL,NULL,7);
/*!40000 ALTER TABLE `callback_requests` ENABLE KEYS */;
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
  `meta_description` varchar(300) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `sort_order` int(11) NOT NULL,
  `parent_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKoul14ho7bctbefv8jywp5v3i2` (`slug`),
  KEY `FKsaok720gsu4u2wrgbk10b5n8d` (`parent_id`),
  CONSTRAINT `FKsaok720gsu4u2wrgbk10b5n8d` FOREIGN KEY (`parent_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `categories`
--

LOCK TABLES `categories` WRITE;
/*!40000 ALTER TABLE `categories` DISABLE KEYS */;
INSERT INTO `categories` VALUES (1,'bi-thermometer-half',NULL,'Giảm đau - Hạ sốt','giam-dau-ha-sot',1,11),(2,'bi-capsule',NULL,'Kháng sinh - Kháng khuẩn','khang-sinh-khang-khuan',2,11),(3,'bi-heart-pulse',NULL,'Tim mạch - Huyết áp','tim-mach-huyet-ap',3,11),(4,'bi-droplet-half',NULL,'Tiêu hóa','tieu-hoa',4,11),(5,'bi-lungs',NULL,'Hô hấp - Cảm cúm','ho-hap-cam-cum',5,11),(6,'bi-sun',NULL,'Vitamin & Khoáng chất','vitamin-khoang-chat',6,NULL),(7,'bi-flower1',NULL,'Thực phẩm chức năng','thuc-pham-chuc-nang',7,NULL),(8,'bi-bandaid',NULL,'Dụng cụ y tế','dung-cu-y-te',8,NULL),(9,'bi-stars',NULL,'Chăm sóc da','cham-soc-da',9,NULL),(10,'bi-activity',NULL,'Thần kinh','than-kinh',10,11),(11,'bi-capsule-pill',NULL,'Thuốc','thuoc',0,NULL),(12,'bi-heart-pulse',NULL,'Thuốc huyết áp','thuoc-huyet-ap',0,3);
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
  `ai_misses` int(11) DEFAULT NULL,
  `ai_summary` varchar(1500) DEFAULT NULL,
  `closed` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `handed_off_at` datetime(6) DEFAULT NULL,
  `handoff_reason` varchar(200) DEFAULT NULL,
  `mode` varchar(10) DEFAULT NULL,
  `triage_asked` bit(1) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `customer_id` bigint(20) NOT NULL,
  `pharmacist_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKaim02rk3jmh6iu2532wid9ukn` (`customer_id`),
  KEY `FKf6y0lwc2qnwgd1h1xnh97o7tj` (`pharmacist_id`),
  CONSTRAINT `FKaim02rk3jmh6iu2532wid9ukn` FOREIGN KEY (`customer_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKf6y0lwc2qnwgd1h1xnh97o7tj` FOREIGN KEY (`pharmacist_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `conversations`
--

LOCK TABLES `conversations` WRITE;
/*!40000 ALTER TABLE `conversations` DISABLE KEYS */;
INSERT INTO `conversations` VALUES (1,NULL,NULL,'\0','2026-09-28 19:48:04.000000',NULL,NULL,NULL,NULL,'2026-09-28 19:58:04.000000',8,2),(2,NULL,'Khách nữ đang mang thai 5 tháng, bị cảm 2 ngày: sổ mũi, đau họng nhẹ, không sốt. Chưa dùng thuốc gì. Hỏi thuốc cảm dùng được khi mang thai. Cần hỏi thêm: dị ứng thuốc, bệnh nền.','\0','2026-09-28 20:12:04.000000','2026-09-28 20:13:04.000000','Đối tượng đặc biệt: \"mang thai\"','HUMAN',NULL,'2026-09-28 20:13:04.000000',7,NULL);
/*!40000 ALTER TABLE `conversations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `drug_interactions`
--

DROP TABLE IF EXISTS `drug_interactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `drug_interactions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `ingredienta` varchar(100) NOT NULL,
  `ingredientb` varchar(100) NOT NULL,
  `level` varchar(10) NOT NULL,
  `message` varchar(500) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `drug_interactions`
--

LOCK TABLES `drug_interactions` WRITE;
/*!40000 ALTER TABLE `drug_interactions` DISABLE KEYS */;
INSERT INTO `drug_interactions` VALUES (1,'ibuprofen','aspirin','danger','Phối hợp hai NSAID làm tăng nguy cơ loét, xuất huyết tiêu hóa.'),(2,'ibuprofen','losartan','warning','NSAID làm giảm tác dụng hạ áp của losartan, tăng nguy cơ suy thận.'),(3,'ibuprofen','amlodipin','warning','NSAID có thể làm giảm tác dụng hạ huyết áp.'),(4,'ibuprofen','bisoprolol','warning','NSAID có thể làm giảm tác dụng hạ huyết áp của bisoprolol.'),(5,'phenylephrin','bisoprolol','warning','Phenylephrin gây co mạch, tăng huyết áp - đối kháng thuốc hạ áp.'),(6,'phenylephrin','amlodipin','warning','Phenylephrin gây tăng huyết áp - đối kháng thuốc hạ áp.'),(7,'phenylephrin','losartan','warning','Phenylephrin gây tăng huyết áp - đối kháng thuốc hạ áp.'),(8,'caffeine','bisoprolol','warning','Caffeine có thể làm tăng nhịp tim, huyết áp.'),(9,'omeprazol','clopidogrel','danger','Omeprazol làm giảm tác dụng chống kết tập tiểu cầu của clopidogrel.'),(10,'dầu cá','aspirin','warning','Dầu cá liều cao phối hợp thuốc chống kết tập tiểu cầu làm tăng nguy cơ chảy máu.'),(11,'bạch quả','aspirin','warning','Cao bạch quả phối hợp aspirin/thuốc chống đông làm tăng nguy cơ chảy máu.'),(12,'bạch quả','ibuprofen','warning','Cao bạch quả phối hợp NSAID làm tăng nguy cơ chảy máu.'),(13,'bacillus','amoxicillin','info','Uống men vi sinh cách kháng sinh ít nhất 2 giờ.'),(14,'bacillus','cefuroxim','info','Uống men vi sinh cách kháng sinh ít nhất 2 giờ.'),(15,'calci','cefuroxim','info','Nên uống calci cách kháng sinh 2 giờ để tránh giảm hấp thu.'),(16,'diazepam','phenylephrin','warning','Cần thận trọng khi phối hợp thuốc an thần với thuốc cảm.');
/*!40000 ALTER TABLE `drug_interactions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ingredients`
--

DROP TABLE IF EXISTS `ingredients`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ingredients` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `drug_group` varchar(150) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj6tsl15xx76y4kv41yxr4uxab` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ingredients`
--

LOCK TABLES `ingredients` WRITE;
/*!40000 ALTER TABLE `ingredients` DISABLE KEYS */;
INSERT INTO `ingredients` VALUES (1,NULL,'Paracetamol',NULL),(2,NULL,'Caffeine',NULL),(3,NULL,'Ibuprofen',NULL),(4,NULL,'Amoxicillin',NULL),(5,NULL,'Acid clavulanic',NULL),(6,NULL,'Cefuroxim',NULL),(7,NULL,'Amlodipin',NULL),(8,NULL,'Bisoprolol fumarat',NULL),(9,NULL,'Losartan kali',NULL),(10,NULL,'Diosmectit',NULL),(11,NULL,'Berberin clorid',NULL),(12,NULL,'Omeprazol',NULL),(13,NULL,'Bacillus clausii',NULL),(14,NULL,'Phenylephrin',NULL),(15,NULL,'Cao lá thường xuân',NULL),(16,NULL,'Acid ascorbic',NULL),(17,NULL,'Calci',NULL),(18,NULL,'Vitamin D3',NULL),(19,NULL,'Dầu cá (EPA',NULL),(20,NULL,'DHA)',NULL),(21,NULL,'Cao bạch quả',NULL),(22,NULL,'Diazepam',NULL);
/*!40000 ALTER TABLE `ingredients` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `manufacturers`
--

DROP TABLE IF EXISTS `manufacturers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `manufacturers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `country` varchar(80) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `website` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKrgruuf4bdtokowdxk169bs8op` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `manufacturers`
--

LOCK TABLES `manufacturers` WRITE;
/*!40000 ALTER TABLE `manufacturers` DISABLE KEYS */;
INSERT INTO `manufacturers` VALUES (1,'Việt Nam','GSK',NULL,NULL),(2,'Pháp','UPSA',NULL,NULL),(3,'Việt Nam','DHG Pharma',NULL,NULL),(4,'Việt Nam','Stada Việt Nam',NULL,NULL),(5,'Anh','GlaxoSmithKline',NULL,NULL),(6,'Việt Nam','Domesco',NULL,NULL),(7,'Đức','Merck',NULL,NULL),(8,'Việt Nam','Pymepharco',NULL,NULL),(9,'Pháp','Ipsen',NULL,NULL),(10,'Việt Nam','Mekophar',NULL,NULL),(11,'Ý','Sanofi',NULL,NULL),(12,'Việt Nam','United Pharma',NULL,NULL),(13,'Đức','Engelhard',NULL,NULL),(14,'Việt Nam','Bidiphar',NULL,NULL),(15,'Úc','Blackmores',NULL,NULL),(16,'Việt Nam','Traphaco',NULL,NULL),(17,'Nhật Bản','Omron',NULL,NULL),(18,'Thụy Sĩ','Microlife',NULL,NULL),(19,'Việt Nam','Nam Anh',NULL,NULL),(20,'Pháp','La Roche-Posay',NULL,NULL),(21,'Canada','Galderma',NULL,NULL),(22,'Hungary','Gedeon Richter',NULL,NULL);
/*!40000 ALTER TABLE `manufacturers` ENABLE KEYS */;
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
  `kind` varchar(10) DEFAULT NULL,
  `conversation_id` bigint(20) NOT NULL,
  `sender_id` bigint(20) DEFAULT NULL,
  `suggested_cart_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `IDX8mkvn4w9p4rq8r7o524h8m04u` (`conversation_id`),
  KEY `FK4ui4nnwntodh6wjvck53dbk9m` (`sender_id`),
  KEY `FK3tvac38gw7ctljglaq5j2c72q` (`suggested_cart_id`),
  CONSTRAINT `FK3tvac38gw7ctljglaq5j2c72q` FOREIGN KEY (`suggested_cart_id`) REFERENCES `suggested_carts` (`id`),
  CONSTRAINT `FK4ui4nnwntodh6wjvck53dbk9m` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKt492th6wsovh1nush5yl5jj8e` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
INSERT INTO `messages` VALUES (1,'Chào dược sĩ, tôi bị tăng huyết áp, có dùng được Decolgen khi bị cảm không ạ?','2026-09-28 19:48:04.000000',NULL,NULL,1,8,NULL),(2,'Chào anh, Decolgen có chứa phenylephrin có thể làm tăng huyết áp, anh không nên dùng. Anh có thể dùng paracetamol đơn thuần để hạ sốt, giảm đau và rửa mũi bằng nước muối sinh lý nhé.','2026-09-28 19:58:04.000000',NULL,NULL,1,2,NULL),(3,'Mình đang mang thai 5 tháng, bị cảm 2 ngày nay sổ mũi, đau họng, uống thuốc gì được ạ?','2026-09-28 20:12:04.000000',NULL,NULL,2,7,NULL),(4,'Với phụ nữ mang thai / cho con bú, trẻ nhỏ hoặc người có bệnh gan, thận, việc dùng thuốc cần dược sĩ tư vấn trực tiếp. Mình chuyển bạn cho dược sĩ nhé.','2026-09-28 20:13:04.000000',NULL,'AI',2,NULL,NULL),(5,'Đã chuyển cuộc trò chuyện cho dược sĩ. Hiện chưa có dược sĩ online, dược sĩ sẽ trả lời sớm nhất (hoặc bạn có thể để lại số để được gọi lại).','2026-09-28 20:13:04.000000',NULL,'SYSTEM',2,NULL,NULL);
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
INSERT INTO `notifications` VALUES (1,'2026-09-28 20:18:06.000000','/staff/prescriptions','Đơn DHDEMORX1 có thuốc kê đơn cần duyệt','\0',2);
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
  `status` varchar(20) NOT NULL,
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
INSERT INTO `order_history` VALUES (1,'2026-08-31 09:15:04.000000','Khách đặt hàng','PENDING',1,7),(2,'2026-09-01 09:15:04.000000','Giao hàng thành công','COMPLETED',1,2),(3,'2026-08-31 12:15:04.000000','Khách đặt hàng','PENDING',2,8),(4,'2026-09-01 12:15:04.000000','Giao hàng thành công','COMPLETED',2,3),(5,'2026-09-01 09:15:04.000000','Khách đặt hàng','PENDING',3,6),(6,'2026-09-02 09:15:04.000000','Giao hàng thành công','COMPLETED',3,2),(7,'2026-09-02 09:15:04.000000','Khách đặt hàng','PENDING',4,8),(8,'2026-09-03 09:15:04.000000','Giao hàng thành công','COMPLETED',4,2),(9,'2026-09-02 12:15:04.000000','Khách đặt hàng','PENDING',5,6),(10,'2026-09-03 12:15:04.000000','Giao hàng thành công','COMPLETED',5,3),(11,'2026-09-02 15:15:04.000000','Khách đặt hàng','PENDING',6,7),(12,'2026-09-03 15:15:04.000000','Giao hàng thành công','COMPLETED',6,2),(13,'2026-09-03 09:15:04.000000','Khách đặt hàng','PENDING',7,7),(14,'2026-09-04 09:15:04.000000','Giao hàng thành công','COMPLETED',7,2),(15,'2026-09-03 12:15:04.000000','Khách đặt hàng','PENDING',8,8),(16,'2026-09-04 12:15:04.000000','Giao hàng thành công','COMPLETED',8,3),(17,'2026-09-04 09:15:04.000000','Khách đặt hàng','PENDING',9,6),(18,'2026-09-05 09:15:04.000000','Giao hàng thành công','COMPLETED',9,2),(19,'2026-09-05 09:15:04.000000','Khách đặt hàng','PENDING',10,8),(20,'2026-09-06 09:15:04.000000','Giao hàng thành công','COMPLETED',10,2),(21,'2026-09-05 12:15:04.000000','Khách đặt hàng','PENDING',11,6),(22,'2026-09-06 12:15:04.000000','Giao hàng thành công','COMPLETED',11,3),(23,'2026-09-05 15:15:04.000000','Khách đặt hàng','PENDING',12,7),(24,'2026-09-06 15:15:04.000000','Giao hàng thành công','COMPLETED',12,2),(25,'2026-09-06 09:15:04.000000','Khách đặt hàng','PENDING',13,7),(26,'2026-09-07 09:15:04.000000','Giao hàng thành công','COMPLETED',13,2),(27,'2026-09-06 12:15:04.000000','Khách đặt hàng','PENDING',14,8),(28,'2026-09-07 12:15:04.000000','Giao hàng thành công','COMPLETED',14,3),(29,'2026-09-07 09:15:04.000000','Khách đặt hàng','PENDING',15,6),(30,'2026-09-08 09:15:04.000000','Giao hàng thành công','COMPLETED',15,2),(31,'2026-09-08 09:15:04.000000','Khách đặt hàng','PENDING',16,8),(32,'2026-09-09 09:15:04.000000','Giao hàng thành công','COMPLETED',16,2),(33,'2026-09-08 12:15:04.000000','Khách đặt hàng','PENDING',17,6),(34,'2026-09-09 12:15:04.000000','Giao hàng thành công','COMPLETED',17,3),(35,'2026-09-08 15:15:04.000000','Khách đặt hàng','PENDING',18,7),(36,'2026-09-09 15:15:04.000000','Giao hàng thành công','COMPLETED',18,2),(37,'2026-09-09 09:15:04.000000','Khách đặt hàng','PENDING',19,7),(38,'2026-09-10 09:15:04.000000','Giao hàng thành công','COMPLETED',19,2),(39,'2026-09-09 12:15:04.000000','Khách đặt hàng','PENDING',20,8),(40,'2026-09-10 12:15:04.000000','Giao hàng thành công','COMPLETED',20,3),(41,'2026-09-10 09:15:04.000000','Khách đặt hàng','PENDING',21,6),(42,'2026-09-11 09:15:04.000000','Giao hàng thành công','COMPLETED',21,2),(43,'2026-09-11 09:15:04.000000','Khách đặt hàng','PENDING',22,8),(44,'2026-09-12 09:15:04.000000','Giao hàng thành công','COMPLETED',22,2),(45,'2026-09-11 12:15:04.000000','Khách đặt hàng','PENDING',23,6),(46,'2026-09-12 12:15:04.000000','Giao hàng thành công','COMPLETED',23,3),(47,'2026-09-11 15:15:04.000000','Khách đặt hàng','PENDING',24,7),(48,'2026-09-12 15:15:04.000000','Giao hàng thành công','COMPLETED',24,2),(49,'2026-09-12 09:15:04.000000','Khách đặt hàng','PENDING',25,7),(50,'2026-09-13 09:15:04.000000','Giao hàng thành công','COMPLETED',25,2),(51,'2026-09-12 12:15:04.000000','Khách đặt hàng','PENDING',26,8),(52,'2026-09-13 12:15:04.000000','Giao hàng thành công','COMPLETED',26,3),(53,'2026-09-13 09:15:04.000000','Khách đặt hàng','PENDING',27,6),(54,'2026-09-14 09:15:04.000000','Giao hàng thành công','COMPLETED',27,2),(55,'2026-09-14 09:15:04.000000','Khách đặt hàng','PENDING',28,8),(56,'2026-09-15 09:15:04.000000','Giao hàng thành công','COMPLETED',28,2),(57,'2026-09-14 12:15:04.000000','Khách đặt hàng','PENDING',29,6),(58,'2026-09-15 12:15:04.000000','Giao hàng thành công','COMPLETED',29,3),(59,'2026-09-14 15:15:04.000000','Khách đặt hàng','PENDING',30,7),(60,'2026-09-15 15:15:04.000000','Giao hàng thành công','COMPLETED',30,2),(61,'2026-09-15 09:15:04.000000','Khách đặt hàng','PENDING',31,7),(62,'2026-09-16 09:15:04.000000','Giao hàng thành công','COMPLETED',31,2),(63,'2026-09-15 12:15:04.000000','Khách đặt hàng','PENDING',32,8),(64,'2026-09-16 12:15:04.000000','Giao hàng thành công','COMPLETED',32,3),(65,'2026-09-16 09:15:04.000000','Khách đặt hàng','PENDING',33,6),(66,'2026-09-17 09:15:04.000000','Giao hàng thành công','COMPLETED',33,2),(67,'2026-09-17 09:15:04.000000','Khách đặt hàng','PENDING',34,8),(68,'2026-09-18 09:15:04.000000','Giao hàng thành công','COMPLETED',34,2),(69,'2026-09-17 12:15:04.000000','Khách đặt hàng','PENDING',35,6),(70,'2026-09-18 12:15:04.000000','Giao hàng thành công','COMPLETED',35,3),(71,'2026-09-17 15:15:04.000000','Khách đặt hàng','PENDING',36,7),(72,'2026-09-18 15:15:04.000000','Giao hàng thành công','COMPLETED',36,2),(73,'2026-09-18 09:15:04.000000','Khách đặt hàng','PENDING',37,7),(74,'2026-09-19 09:15:04.000000','Giao hàng thành công','COMPLETED',37,2),(75,'2026-09-18 12:15:04.000000','Khách đặt hàng','PENDING',38,8),(76,'2026-09-19 12:15:04.000000','Giao hàng thành công','COMPLETED',38,3),(77,'2026-09-19 09:15:04.000000','Khách đặt hàng','PENDING',39,6),(78,'2026-09-20 09:15:04.000000','Giao hàng thành công','COMPLETED',39,2),(79,'2026-09-20 09:15:04.000000','Khách đặt hàng','PENDING',40,8),(80,'2026-09-21 09:15:04.000000','Giao hàng thành công','COMPLETED',40,2),(81,'2026-09-20 12:15:04.000000','Khách đặt hàng','PENDING',41,6),(82,'2026-09-21 12:15:04.000000','Giao hàng thành công','COMPLETED',41,3),(83,'2026-09-20 15:15:04.000000','Khách đặt hàng','PENDING',42,7),(84,'2026-09-21 15:15:04.000000','Giao hàng thành công','COMPLETED',42,2),(85,'2026-09-21 09:15:04.000000','Khách đặt hàng','PENDING',43,7),(86,'2026-09-22 09:15:04.000000','Giao hàng thành công','COMPLETED',43,2),(87,'2026-09-21 12:15:04.000000','Khách đặt hàng','PENDING',44,8),(88,'2026-09-22 12:15:04.000000','Giao hàng thành công','COMPLETED',44,3),(89,'2026-09-22 09:15:04.000000','Khách đặt hàng','PENDING',45,6),(90,'2026-09-23 09:15:04.000000','Giao hàng thành công','COMPLETED',45,2),(91,'2026-09-23 09:15:04.000000','Khách đặt hàng','PENDING',46,8),(92,'2026-09-24 09:15:04.000000','Giao hàng thành công','COMPLETED',46,2),(93,'2026-09-23 12:15:04.000000','Khách đặt hàng','PENDING',47,6),(94,'2026-09-24 12:15:04.000000','Giao hàng thành công','COMPLETED',47,3),(95,'2026-09-23 15:15:04.000000','Khách đặt hàng','PENDING',48,7),(96,'2026-09-24 15:15:04.000000','Giao hàng thành công','COMPLETED',48,2),(97,'2026-09-24 09:15:04.000000','Khách đặt hàng','PENDING',49,7),(98,'2026-09-25 09:15:04.000000','Giao hàng thành công','COMPLETED',49,2),(99,'2026-09-24 12:15:04.000000','Khách đặt hàng','PENDING',50,8),(100,'2026-09-25 12:15:04.000000','Giao hàng thành công','COMPLETED',50,3),(101,'2026-09-25 09:15:04.000000','Khách đặt hàng','PENDING',51,6),(102,'2026-09-26 09:15:04.000000','Giao hàng thành công','COMPLETED',51,2),(103,'2026-09-26 09:15:04.000000','Khách đặt hàng','PENDING',52,8),(104,'2026-09-27 09:15:04.000000','Giao hàng thành công','COMPLETED',52,2),(105,'2026-09-26 12:15:04.000000','Khách đặt hàng','PENDING',53,6),(106,'2026-09-27 12:15:04.000000','Giao hàng thành công','COMPLETED',53,3),(107,'2026-09-26 15:15:04.000000','Khách đặt hàng','PENDING',54,7),(108,'2026-09-27 15:15:04.000000','Giao hàng thành công','COMPLETED',54,2),(109,'2026-09-27 09:15:04.000000','Khách đặt hàng','PENDING',55,7),(110,'2026-09-28 09:15:04.000000','Giao hàng thành công','COMPLETED',55,2),(111,'2026-09-27 12:15:04.000000','Khách đặt hàng','PENDING',56,8),(112,'2026-09-28 12:15:04.000000','Giao hàng thành công','COMPLETED',56,3),(113,'2026-09-28 18:18:04.000000','Khách đặt hàng kèm đơn thuốc','PENDING_RX',57,6),(114,'2026-09-28 19:18:04.000000','Khách đặt hàng','PENDING',58,7);
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
  `drug_type` varchar(20) DEFAULT NULL,
  `price` bigint(20) NOT NULL,
  `product_name` varchar(200) NOT NULL,
  `quantity` int(11) NOT NULL,
  `unit` varchar(30) DEFAULT NULL,
  `unit_factor` int(11) DEFAULT NULL,
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
INSERT INTO `order_items` VALUES (1,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,1,20),(2,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',NULL,1,24),(3,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,2,21),(4,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,2,4),(5,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',NULL,3,17),(6,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',NULL,4,14),(7,'OTC',42000,'Hapacol 250 bột sủi trẻ em',2,'Hộp',NULL,4,3),(8,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,5,15),(9,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',NULL,6,16),(10,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',2,'Hộp',NULL,6,20),(11,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,7,4),(12,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,7,21),(13,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,8,11),(14,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,8,1),(15,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,9,1),(16,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',NULL,9,14),(17,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',NULL,10,23),(18,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',1,'Chai',NULL,10,25),(19,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',NULL,11,24),(20,'OTC',115000,'Smecta hương cam',1,'Hộp',NULL,11,11),(21,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',NULL,12,25),(22,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',1,'Tuýp',NULL,12,17),(23,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,13,20),(24,'SUPPLEMENT',110000,'Canxi D3 Corbiere',2,'Hộp',NULL,13,18),(25,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,14,21),(26,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',NULL,14,23),(27,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',NULL,15,17),(28,'OTC',7000,'Ibuprofen 400mg',1,'Vỉ',NULL,15,4),(29,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',NULL,16,14),(30,'DEVICE',75000,'Nhiệt kế điện tử Microlife MT200',2,'Cái',NULL,16,22),(31,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,17,15),(32,'OTC',12500,'Efferalgan 500mg viên sủi',2,'Vỉ',NULL,17,2),(33,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',NULL,18,16),(34,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',NULL,18,14),(35,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,19,4),(36,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,19,15),(37,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,20,11),(38,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,20,20),(39,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,21,1),(40,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',NULL,22,23),(41,'SUPPLEMENT',395000,'Omega-3 Fish Oil 1000mg',1,'Lọ',NULL,22,19),(42,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',NULL,23,24),(43,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',NULL,24,25),(44,'OTC',7000,'Ibuprofen 400mg',1,'Vỉ',NULL,24,4),(45,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,25,20),(46,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,25,11),(47,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,26,21),(48,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',NULL,26,17),(49,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',NULL,27,17),(50,'DEVICE',35000,'Khẩu trang y tế 4 lớp',1,'Hộp',NULL,27,23),(51,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',NULL,28,14),(52,'OTC',89000,'Siro ho Prospan 100ml',2,'Chai',NULL,28,16),(53,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,29,15),(54,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',2,'Cái',NULL,29,21),(55,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',NULL,30,16),(56,'OTC',12500,'Panadol Extra',2,'Vỉ',NULL,30,1),(57,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,31,4),(58,'OTC',12500,'Efferalgan 500mg viên sủi',1,'Vỉ',NULL,31,2),(59,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,32,11),(60,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',NULL,32,14),(61,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,33,1),(62,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',2,'Hộp',NULL,33,20),(63,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',NULL,34,23),(64,'OTC',16000,'Berberin 100mg',1,'Lọ',NULL,34,12),(65,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',NULL,35,24),(66,'SUPPLEMENT',110000,'Canxi D3 Corbiere',1,'Hộp',NULL,35,18),(67,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',NULL,36,25),(68,'DEVICE',35000,'Khẩu trang y tế 4 lớp',1,'Hộp',NULL,36,23),(69,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,37,20),(70,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',NULL,37,24),(71,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,38,21),(72,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,38,4),(73,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',NULL,39,17),(74,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',NULL,40,14),(75,'OTC',42000,'Hapacol 250 bột sủi trẻ em',2,'Hộp',NULL,40,3),(76,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,41,15),(77,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',NULL,42,16),(78,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',2,'Hộp',NULL,42,20),(79,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,43,4),(80,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,43,21),(81,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,44,11),(82,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,44,1),(83,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,45,1),(84,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',NULL,45,14),(85,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',NULL,46,23),(86,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',1,'Chai',NULL,46,25),(87,'COSMETIC',485000,'Kem chống nắng La Roche-Posay Anthelios SPF50+',2,'Tuýp',NULL,47,24),(88,'OTC',115000,'Smecta hương cam',1,'Hộp',NULL,47,11),(89,'COSMETIC',345000,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',2,'Chai',NULL,48,25),(90,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',1,'Tuýp',NULL,48,17),(91,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,49,20),(92,'SUPPLEMENT',110000,'Canxi D3 Corbiere',2,'Hộp',NULL,49,18),(93,'DEVICE',890000,'Máy đo huyết áp bắp tay Omron HEM-7121',1,'Cái',NULL,50,21),(94,'DEVICE',35000,'Khẩu trang y tế 4 lớp',2,'Hộp',NULL,50,23),(95,'SUPPLEMENT',35000,'Viên sủi Vitamin C 1000mg',2,'Tuýp',NULL,51,17),(96,'OTC',7000,'Ibuprofen 400mg',1,'Vỉ',NULL,51,4),(97,'OTC',165000,'Enterogermina 2 tỷ/5ml',1,'Hộp',NULL,52,14),(98,'DEVICE',75000,'Nhiệt kế điện tử Microlife MT200',2,'Cái',NULL,52,22),(99,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,53,15),(100,'OTC',12500,'Efferalgan 500mg viên sủi',2,'Vỉ',NULL,53,2),(101,'OTC',89000,'Siro ho Prospan 100ml',1,'Chai',NULL,54,16),(102,'OTC',165000,'Enterogermina 2 tỷ/5ml',2,'Hộp',NULL,54,14),(103,'OTC',7000,'Ibuprofen 400mg',2,'Vỉ',NULL,55,4),(104,'OTC',5000,'Decolgen ND',1,'Vỉ',NULL,55,15),(105,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,56,11),(106,'SUPPLEMENT',99000,'Ginkgo Biloba 120mg',1,'Hộp',NULL,56,20),(107,'ETC',110000,'Augmentin 625mg',1,'Vỉ',NULL,57,5),(108,'OTC',12500,'Panadol Extra',1,'Vỉ',NULL,57,1),(109,'OTC',115000,'Smecta hương cam',2,'Hộp',NULL,58,11);
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
  `carrier` varchar(50) DEFAULT NULL,
  `channel` varchar(10) DEFAULT NULL,
  `code` varchar(30) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `discount` bigint(20) NOT NULL,
  `einvoice_no` varchar(50) DEFAULT NULL,
  `needs_prescription` bit(1) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `payment_method` varchar(20) NOT NULL,
  `payment_status` varchar(20) NOT NULL,
  `phone` varchar(20) NOT NULL,
  `points_discount` bigint(20) DEFAULT NULL,
  `points_earned` int(11) DEFAULT NULL,
  `points_used` int(11) DEFAULT NULL,
  `promo_discount` bigint(20) DEFAULT NULL,
  `promo_note` varchar(500) DEFAULT NULL,
  `province` varchar(50) DEFAULT NULL,
  `recipient` varchar(100) NOT NULL,
  `refund_amount` bigint(20) DEFAULT NULL,
  `refund_note` varchar(300) DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `return_reason` varchar(1000) DEFAULT NULL,
  `return_status` varchar(20) DEFAULT NULL,
  `shipping_fee` bigint(20) NOT NULL,
  `shipping_method` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL,
  `subtotal` bigint(20) NOT NULL,
  `total` bigint(20) NOT NULL,
  `tracking_code` varchar(60) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `vat_address` varchar(300) DEFAULT NULL,
  `vat_company` varchar(200) DEFAULT NULL,
  `vat_email` varchar(150) DEFAULT NULL,
  `vat_tax_code` varchar(20) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `verify_note` varchar(300) DEFAULT NULL,
  `voucher_code` varchar(30) DEFAULT NULL,
  `assigned_to_id` bigint(20) DEFAULT NULL,
  `handled_by_id` bigint(20) DEFAULT NULL,
  `refunded_by_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  `verified_by_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKgt3o4a5bqj59e9y6wakgk926t` (`code`),
  KEY `IDXqro50btxtakk2eg9v13c1se48` (`status`),
  KEY `IDXk8kupdtcdpqd57b6j4yq9uvdj` (`user_id`),
  KEY `FK6ccjiq031c33jcytigjm8p5x1` (`assigned_to_id`),
  KEY `FKi4h5wn8pifrgk16i5fbmbmi2m` (`handled_by_id`),
  KEY `FK6vbtn4bwypk03gh7drpvgun7f` (`refunded_by_id`),
  KEY `FK3wuxt3bis6k6w1v26uxhvv2qg` (`verified_by_id`),
  CONSTRAINT `FK32ql8ubntj5uh44ph9659tiih` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK3wuxt3bis6k6w1v26uxhvv2qg` FOREIGN KEY (`verified_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6ccjiq031c33jcytigjm8p5x1` FOREIGN KEY (`assigned_to_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6vbtn4bwypk03gh7drpvgun7f` FOREIGN KEY (`refunded_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKi4h5wn8pifrgk16i5fbmbmi2m` FOREIGN KEY (`handled_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `orders`
--

LOCK TABLES `orders` WRITE;
/*!40000 ALTER TABLE `orders` DISABLE KEYS */;
INSERT INTO `orders` VALUES (1,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928HNXQ','2026-09-01 09:15:04.000000','2026-08-31 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',1069000,1069000,NULL,'2026-08-31 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(2,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609282XF2','2026-09-01 12:15:04.000000','2026-08-31 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',904000,904000,NULL,'2026-08-31 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(3,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928RFEA','2026-09-02 09:15:04.000000','2026-09-01 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',70000,90000,NULL,'2026-09-01 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(4,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609289XXE','2026-09-03 09:15:04.000000','2026-09-02 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',249000,269000,NULL,'2026-09-02 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(5,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928MVQA','2026-09-03 12:15:04.000000','2026-09-02 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',5000,25000,NULL,'2026-09-02 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(6,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928FB92','2026-09-03 15:15:04.000000','2026-09-02 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',287000,307000,NULL,'2026-09-02 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(7,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928GTC8','2026-09-04 09:15:04.000000','2026-09-03 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',904000,904000,NULL,'2026-09-03 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(8,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928N3KA','2026-09-04 12:15:04.000000','2026-09-03 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',242500,262500,NULL,'2026-09-03 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(9,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928JV4G','2026-09-05 09:15:04.000000','2026-09-04 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',342500,342500,NULL,'2026-09-04 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(10,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928ABEW','2026-09-06 09:15:04.000000','2026-09-05 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',415000,415000,NULL,'2026-09-05 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(11,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928763L','2026-09-06 12:15:04.000000','2026-09-05 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',1085000,1085000,NULL,'2026-09-05 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(12,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928SRZN','2026-09-06 15:15:04.000000','2026-09-05 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',725000,725000,NULL,'2026-09-05 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(13,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928E8HF','2026-09-07 09:15:04.000000','2026-09-06 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',319000,319000,NULL,'2026-09-06 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(14,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609285GRS','2026-09-07 12:15:04.000000','2026-09-06 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',960000,960000,NULL,'2026-09-06 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(15,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928WG55','2026-09-08 09:15:04.000000','2026-09-07 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',77000,97000,NULL,'2026-09-07 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(16,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928C79T','2026-09-09 09:15:04.000000','2026-09-08 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',315000,315000,NULL,'2026-09-08 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(17,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928RSNA','2026-09-09 12:15:04.000000','2026-09-08 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',30000,50000,NULL,'2026-09-08 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(18,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928PQAG','2026-09-09 15:15:04.000000','2026-09-08 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',419000,419000,NULL,'2026-09-08 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(19,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928XTGB','2026-09-10 09:15:04.000000','2026-09-09 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',19000,39000,NULL,'2026-09-09 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(20,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609285JG8','2026-09-10 12:15:04.000000','2026-09-09 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',329000,329000,NULL,'2026-09-09 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(21,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH2609288UN7','2026-09-11 09:15:04.000000','2026-09-10 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',12500,32500,NULL,'2026-09-10 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(22,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928FU5H','2026-09-12 09:15:04.000000','2026-09-11 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',465000,465000,NULL,'2026-09-11 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(23,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928EUVS','2026-09-12 12:15:04.000000','2026-09-11 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',970000,970000,NULL,'2026-09-11 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(24,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH2609287ZGK','2026-09-12 15:15:04.000000','2026-09-11 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',697000,697000,NULL,'2026-09-11 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(25,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928HRBT','2026-09-13 09:15:04.000000','2026-09-12 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',329000,329000,NULL,'2026-09-12 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(26,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609283Y4Y','2026-09-13 12:15:04.000000','2026-09-12 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',960000,960000,NULL,'2026-09-12 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(27,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928R2ZA','2026-09-14 09:15:04.000000','2026-09-13 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',105000,125000,NULL,'2026-09-13 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(28,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928QNAD','2026-09-15 09:15:04.000000','2026-09-14 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',343000,343000,NULL,'2026-09-14 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(29,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928PUPV','2026-09-15 12:15:04.000000','2026-09-14 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',1785000,1785000,NULL,'2026-09-14 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(30,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928KV5P','2026-09-15 15:15:04.000000','2026-09-14 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',114000,134000,NULL,'2026-09-14 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(31,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928UWSS','2026-09-16 09:15:04.000000','2026-09-15 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',26500,46500,NULL,'2026-09-15 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(32,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928BS8B','2026-09-16 12:15:04.000000','2026-09-15 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',395000,395000,NULL,'2026-09-15 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(33,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH26092855GV','2026-09-17 09:15:04.000000','2026-09-16 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',210500,230500,NULL,'2026-09-16 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(34,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928S857','2026-09-18 09:15:04.000000','2026-09-17 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',86000,106000,NULL,'2026-09-17 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(35,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928NQFX','2026-09-18 12:15:04.000000','2026-09-17 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',1080000,1080000,NULL,'2026-09-17 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(36,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH2609284X22','2026-09-18 15:15:04.000000','2026-09-17 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',725000,725000,NULL,'2026-09-17 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(37,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928E3JK','2026-09-19 09:15:04.000000','2026-09-18 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',1069000,1069000,NULL,'2026-09-18 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(38,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928KLE5','2026-09-19 12:15:04.000000','2026-09-18 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',904000,904000,NULL,'2026-09-18 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(39,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928K35P','2026-09-20 09:15:04.000000','2026-09-19 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',70000,90000,NULL,'2026-09-19 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(40,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928SQHK','2026-09-21 09:15:04.000000','2026-09-20 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',249000,269000,NULL,'2026-09-20 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(41,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928F89X','2026-09-21 12:15:04.000000','2026-09-20 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',5000,25000,NULL,'2026-09-20 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(42,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928Y632','2026-09-21 15:15:04.000000','2026-09-20 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',287000,307000,NULL,'2026-09-20 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(43,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928WVWX','2026-09-22 09:15:04.000000','2026-09-21 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',904000,904000,NULL,'2026-09-21 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(44,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609283NWA','2026-09-22 12:15:04.000000','2026-09-21 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',242500,262500,NULL,'2026-09-21 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(45,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928CNLS','2026-09-23 09:15:04.000000','2026-09-22 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',342500,342500,NULL,'2026-09-22 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(46,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928JPPQ','2026-09-24 09:15:04.000000','2026-09-23 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',415000,415000,NULL,'2026-09-23 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(47,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928XHNQ','2026-09-24 12:15:04.000000','2026-09-23 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',1085000,1085000,NULL,'2026-09-23 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(48,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928GJK5','2026-09-24 15:15:04.000000','2026-09-23 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',725000,725000,NULL,'2026-09-23 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(49,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH2609284WBQ','2026-09-25 09:15:04.000000','2026-09-24 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',319000,319000,NULL,'2026-09-24 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(50,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928DBXY','2026-09-25 12:15:04.000000','2026-09-24 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',960000,960000,NULL,'2026-09-24 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(51,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928VLQR','2026-09-26 09:15:04.000000','2026-09-25 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',77000,97000,NULL,'2026-09-25 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,6,NULL),(52,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH260928PSZ8','2026-09-27 09:15:04.000000','2026-09-26 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',315000,315000,NULL,'2026-09-26 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,8,NULL),(53,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DH260928FXRX','2026-09-27 12:15:04.000000','2026-09-26 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',30000,50000,NULL,'2026-09-26 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,6,NULL),(54,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH260928ZLBP','2026-09-27 15:15:04.000000','2026-09-26 15:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',419000,419000,NULL,'2026-09-26 15:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(55,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DH2609285BZE','2026-09-28 09:15:04.000000','2026-09-27 09:15:04.000000',0,NULL,'\0',NULL,'COD','PAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','COMPLETED',19000,39000,NULL,'2026-09-27 09:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,2,NULL,7,NULL),(56,'88 Nguyễn Huệ, Bến Nghé, Quận 1, TP.HCM',NULL,NULL,NULL,'DH2609282KYR','2026-09-28 12:15:04.000000','2026-09-27 12:15:04.000000',0,NULL,'\0',NULL,'ONLINE','PAID','0934567890',NULL,NULL,NULL,NULL,NULL,NULL,'Hoàng Minh Châu',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','COMPLETED',329000,329000,NULL,'2026-09-27 12:15:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,3,NULL,8,NULL),(57,'45 Lê Văn Lương, Nhân Chính, Thanh Xuân, Hà Nội',NULL,NULL,NULL,'DHDEMORX1',NULL,'2026-09-28 18:18:04.000000',0,NULL,'','Giao giờ hành chính','COD','UNPAID','0912345678',NULL,NULL,NULL,NULL,NULL,NULL,'Trần Văn An',NULL,NULL,NULL,NULL,NULL,0,'DELIVERY','PENDING_RX',122500,122500,NULL,'2026-09-28 18:18:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,6,NULL),(58,'12 Trần Duy Hưng, Trung Hòa, Cầu Giấy, Hà Nội',NULL,NULL,NULL,'DHDEMO002',NULL,'2026-09-28 19:18:04.000000',0,NULL,'\0',NULL,'COD','UNPAID','0987654321',NULL,NULL,NULL,NULL,NULL,NULL,'Lê Thị Bình',NULL,NULL,NULL,NULL,NULL,20000,'DELIVERY','PENDING',230000,250000,NULL,'2026-09-28 19:18:04.000000',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,7,NULL);
/*!40000 ALTER TABLE `orders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_requests`
--

DROP TABLE IF EXISTS `password_reset_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `password_reset_requests` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `contact_phone` varchar(20) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `handled` bit(1) NOT NULL,
  `handled_at` datetime(6) DEFAULT NULL,
  `identifier` varchar(150) NOT NULL,
  `note` varchar(300) DEFAULT NULL,
  `handled_by_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeijug1wqsq8fa1xb7xm5temef` (`handled_by_id`),
  KEY `FK1xtvwnh0xfmemjmmgamr3y83f` (`user_id`),
  CONSTRAINT `FK1xtvwnh0xfmemjmmgamr3y83f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKeijug1wqsq8fa1xb7xm5temef` FOREIGN KEY (`handled_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_requests`
--

LOCK TABLES `password_reset_requests` WRITE;
/*!40000 ALTER TABLE `password_reset_requests` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_requests` ENABLE KEYS */;
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
INSERT INTO `posts` VALUES (1,'Paracetamol là thuốc hạ sốt được khuyến cáo phổ biến cho trẻ em. Liều thường dùng là 10-15mg/kg cân nặng mỗi lần, cách nhau 4-6 giờ, không quá 4-5 lần/ngày.\n\nChỉ nên dùng thuốc hạ sốt khi trẻ sốt từ 38,5°C trở lên. Với mức sốt thấp hơn, hãy cho trẻ mặc thoáng, uống nhiều nước và lau người bằng nước ấm.\n\nKhông phối hợp nhiều sản phẩm cùng chứa paracetamol (ví dụ thuốc cảm và thuốc hạ sốt) vì dễ gây quá liều, ảnh hưởng đến gan.\n\nĐưa trẻ đi khám ngay nếu trẻ dưới 3 tháng tuổi bị sốt, sốt cao liên tục trên 2 ngày, co giật, li bì hoặc nôn nhiều.','2026-09-25 20:18:04.000000','','cach-dung-thuoc-ha-sot-dung-cho-tre-em','Hướng dẫn cha mẹ dùng paracetamol an toàn, đúng liều theo cân nặng của trẻ.','Cách dùng thuốc hạ sốt đúng cho trẻ em',2),(2,'Kháng sinh chỉ có tác dụng với vi khuẩn, không có tác dụng với virus gây cảm cúm thông thường. Việc tự ý dùng kháng sinh khi không cần thiết vừa không hiệu quả, vừa làm tăng nguy cơ kháng thuốc.\n\nTheo quy định, kháng sinh là thuốc kê đơn - nhà thuốc chỉ được bán khi có đơn của bác sĩ. Đó cũng là lý do website yêu cầu bạn tải lên đơn thuốc khi mua các sản phẩm này.\n\nKhi được kê kháng sinh, hãy uống đủ liều, đủ thời gian, kể cả khi đã thấy đỡ. Ngưng thuốc sớm tạo điều kiện cho vi khuẩn sống sót và trở nên kháng thuốc.','2026-09-22 20:18:04.000000','','vi-sao-khong-nen-tu-y-dung-khang-sinh','Lạm dụng kháng sinh là nguyên nhân chính dẫn đến tình trạng kháng thuốc.','Vì sao không nên tự ý dùng kháng sinh?',3),(3,'Nên đo huyết áp vào cùng thời điểm mỗi ngày, tốt nhất là buổi sáng trước khi uống thuốc và buổi tối trước khi đi ngủ.\n\nTrước khi đo, ngồi nghỉ 5 phút, không hút thuốc, không uống cà phê trong vòng 30 phút. Đặt tay ngang mức tim, quấn vòng bít vừa khít cánh tay.\n\nGhi lại kết quả vào sổ theo dõi và mang theo khi đi tái khám để bác sĩ điều chỉnh thuốc phù hợp.','2026-09-19 20:18:04.000000','','theo-doi-huyet-ap-tai-nha-nhung-dieu-can-biet','Đo huyết áp đúng cách giúp kiểm soát bệnh tăng huyết áp hiệu quả hơn.','Theo dõi huyết áp tại nhà: những điều cần biết',2);
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
  `checklist` varchar(300) DEFAULT NULL,
  `clinic` varchar(200) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_note` varchar(500) DEFAULT NULL,
  `doctor_name` varchar(100) DEFAULT NULL,
  `image` varchar(200) DEFAULT NULL,
  `patient_name` varchar(100) DEFAULT NULL,
  `pharmacist_note` varchar(1000) DEFAULT NULL,
  `reject_reason` varchar(500) DEFAULT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `rx_date` date DEFAULT NULL,
  `standalone` bit(1) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `order_id` bigint(20) DEFAULT NULL,
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
INSERT INTO `prescriptions` VALUES (1,NULL,NULL,'2026-09-28 18:18:04.000000','Đơn bác sĩ kê hôm nay',NULL,'sample-rx-1.svg',NULL,NULL,NULL,NULL,NULL,NULL,'PENDING',57,NULL,6);
/*!40000 ALTER TABLE `prescriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_equivalents`
--

DROP TABLE IF EXISTS `product_equivalents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `product_equivalents` (
  `product_id` bigint(20) NOT NULL,
  `equivalent_id` bigint(20) NOT NULL,
  PRIMARY KEY (`product_id`,`equivalent_id`),
  KEY `FKleroyyxxet923ojeydmmhwmns` (`equivalent_id`),
  CONSTRAINT `FKb7gowli6k6pom1r8l9ijwlt30` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKleroyyxxet923ojeydmmhwmns` FOREIGN KEY (`equivalent_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_equivalents`
--

LOCK TABLES `product_equivalents` WRITE;
/*!40000 ALTER TABLE `product_equivalents` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_equivalents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_questions`
--

DROP TABLE IF EXISTS `product_questions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `product_questions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `answer` varchar(2000) DEFAULT NULL,
  `answered_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `hidden` bit(1) NOT NULL,
  `question` varchar(1000) NOT NULL,
  `answered_by_id` bigint(20) DEFAULT NULL,
  `product_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6a2dr1vpm7dtolohd1ogi6nn7` (`answered_by_id`),
  KEY `FKmb74hgsft9ibnqeb5kqile518` (`product_id`),
  KEY `FK2316nred1rnt8ho92toi8cr43` (`user_id`),
  CONSTRAINT `FK2316nred1rnt8ho92toi8cr43` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6a2dr1vpm7dtolohd1ogi6nn7` FOREIGN KEY (`answered_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKmb74hgsft9ibnqeb5kqile518` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_questions`
--

LOCK TABLES `product_questions` WRITE;
/*!40000 ALTER TABLE `product_questions` DISABLE KEYS */;
INSERT INTO `product_questions` VALUES (1,'Panadol Extra có chứa caffeine có thể làm tăng nhịp tim, huyết áp. Người tăng huyết áp nên ưu tiên paracetamol đơn thuần và hỏi ý kiến bác sĩ.','2026-09-27 20:18:04.000000','2026-09-28 20:18:06.000000','\0','Người bị tăng huyết áp có dùng Panadol Extra được không ạ?',2,1,8),(2,NULL,NULL,'2026-09-28 20:18:06.000000','\0','Smecta uống trước hay sau bữa ăn ạ? Có uống cùng thuốc khác được không?',NULL,11,6);
/*!40000 ALTER TABLE `product_questions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `product_units`
--

DROP TABLE IF EXISTS `product_units`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `product_units` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `factor` int(11) NOT NULL,
  `name` varchar(30) NOT NULL,
  `price` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5kqda142dwa9lo7vigynbg4sb` (`product_id`),
  CONSTRAINT `FK5kqda142dwa9lo7vigynbg4sb` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_units`
--

LOCK TABLES `product_units` WRITE;
/*!40000 ALTER TABLE `product_units` DISABLE KEYS */;
INSERT INTO `product_units` VALUES (1,15,'Hộp',185000,1),(2,4,'Hộp',48000,2),(3,10,'Hộp',65000,4),(4,2,'Hộp',215000,5),(5,10,'Hộp',85000,6),(6,3,'Hộp',32000,8),(7,3,'Hộp',128000,9),(8,25,'Hộp',125000,15);
/*!40000 ALTER TABLE `product_units` ENABLE KEYS */;
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
  `drug_type` varchar(20) NOT NULL,
  `image` varchar(300) DEFAULT NULL,
  `manufacturer` varchar(150) DEFAULT NULL,
  `max_per_order` int(11) DEFAULT NULL,
  `meta_description` varchar(300) DEFAULT NULL,
  `meta_title` varchar(150) DEFAULT NULL,
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
INSERT INTO `products` VALUES (1,'','Paracetamol, Caffeine','Quá mẫn với paracetamol hoặc caffeine. Suy gan nặng.','Việt Nam','2026-07-30 20:18:04.000000','Giảm các cơn đau nhẹ đến vừa: đau đầu, đau nửa đầu, đau cơ, đau bụng kinh, đau họng, đau răng; hạ sốt.','Viên nén bao phim','OTC',NULL,'GSK',75,NULL,NULL,10,'Panadol Extra',13267,'Hộp 15 vỉ x 12 viên',12500,'VD-21189-14','Hiếm gặp: phát ban, buồn nôn. Dùng quá liều có thể gây tổn thương gan.','panadol-extra','500mg/65mg','Vỉ','Người lớn và trẻ em trên 12 tuổi: 1-2 viên mỗi 4-6 giờ khi cần. Không quá 8 viên/24 giờ.',1),(2,'','Paracetamol','Suy gan. Quá mẫn với paracetamol. Chế độ ăn kiêng muối.','Pháp','2026-07-30 20:19:04.000000','Điều trị triệu chứng các chứng đau và/hoặc sốt như đau đầu, tình trạng như cúm, đau răng, nhức mỏi cơ.','Viên sủi','OTC',NULL,'UPSA',20,NULL,NULL,10,'Efferalgan 500mg viên sủi',NULL,'Hộp 4 vỉ x 4 viên',12500,'VN-19954-16','Phản ứng dị ứng, giảm tiểu cầu (rất hiếm).','efferalgan-500mg-vien-sui','500mg','Vỉ','Hòa tan hoàn toàn viên thuốc vào cốc nước. Người lớn: 1-2 viên/lần, cách nhau ít nhất 4 giờ.',1),(3,'','Paracetamol','Quá mẫn với paracetamol. Trẻ bị suy gan, thiếu G6PD.','Việt Nam','2026-07-30 20:20:04.000000','Hạ sốt, giảm đau cho trẻ em trong các trường hợp cảm cúm, nhiễm khuẩn, mọc răng, sau tiêm chủng.','Bột sủi bọt','OTC',NULL,'DHG Pharma',5,NULL,NULL,10,'Hapacol 250 bột sủi trẻ em',NULL,'Hộp 24 gói x 1,5g',42000,'VD-20560-14','Ít gặp: ban da, buồn nôn.','hapacol-250-bot-sui-tre-em','250mg','Hộp','Trẻ 3-6 tuổi: 1 gói/lần; trẻ 7-12 tuổi: 2 gói/lần. Cách nhau 4-6 giờ, không quá 5 lần/ngày.',1),(4,'','Ibuprofen','Loét dạ dày tá tràng tiến triển, suy gan thận nặng, phụ nữ có thai 3 tháng cuối, hen do aspirin.','Việt Nam','2026-07-30 20:21:04.000000','Giảm đau, kháng viêm trong đau đầu, đau răng, đau bụng kinh, đau cơ xương khớp; hạ sốt.','Viên nén bao phim','OTC',NULL,'Stada Việt Nam',30,NULL,NULL,10,'Ibuprofen 400mg',NULL,'Hộp 10 vỉ x 10 viên',7000,'VD-24520-16','Đau thượng vị, buồn nôn, chóng mặt.','ibuprofen-400mg','400mg','Vỉ','Người lớn: 1 viên x 2-3 lần/ngày, uống sau ăn.',1),(5,'','Amoxicillin, Acid clavulanic','Dị ứng nhóm beta-lactam (penicillin, cephalosporin).','Anh','2026-07-30 20:22:04.000000','Điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, đường tiết niệu, da và mô mềm do vi khuẩn nhạy cảm.','Viên nén bao phim','ETC',NULL,'GlaxoSmithKline',6,NULL,NULL,10,'Augmentin 625mg',NULL,'Hộp 2 vỉ x 7 viên',110000,'VN-20493-17','Tiêu chảy, buồn nôn, phát ban, nhiễm nấm Candida.','augmentin-625mg','500mg/125mg','Vỉ','Theo chỉ định của bác sĩ. Thông thường: 1 viên x 2 lần/ngày, uống đầu bữa ăn.',2),(6,'','Amoxicillin','Dị ứng penicillin.','Việt Nam','2026-07-30 20:23:04.000000','Điều trị nhiễm khuẩn do vi khuẩn nhạy cảm với amoxicillin.','Viên nang cứng','ETC',NULL,'Domesco',30,NULL,NULL,10,'Amoxicillin 500mg Domesco',NULL,'Hộp 10 vỉ x 10 viên',9000,'VD-25013-16','Buồn nôn, tiêu chảy, ban da.','amoxicillin-500mg-domesco','500mg','Vỉ','Theo chỉ định của bác sĩ.',2),(7,'','Cefuroxim','Dị ứng cephalosporin.','Anh','2026-07-30 20:24:04.000000','Kháng sinh cephalosporin thế hệ 2 điều trị nhiễm khuẩn đường hô hấp, tai mũi họng, tiết niệu.','Viên nén bao phim','ETC',NULL,'GlaxoSmithKline',2,NULL,NULL,10,'Zinnat 500mg',NULL,'Hộp 1 vỉ x 10 viên',245000,'VN-18763-15','Tiêu chảy, đau đầu, tăng men gan thoáng qua.','zinnat-500mg','500mg','Hộp','Theo chỉ định của bác sĩ. Uống sau ăn.',2),(8,'','Amlodipin','Quá mẫn với dihydropyridin. Hạ huyết áp nặng, sốc tim.','Việt Nam','2026-07-30 20:25:04.000000','Điều trị tăng huyết áp, đau thắt ngực ổn định.','Viên nén','ETC',NULL,'Stada Việt Nam',15,NULL,NULL,10,'Amlodipin 5mg Stada',NULL,'Hộp 3 vỉ x 10 viên',11000,'VD-23417-15','Phù cổ chân, đỏ bừng mặt, đau đầu.','amlodipin-5mg-stada','5mg','Vỉ','Theo chỉ định của bác sĩ. Thường 1 viên/ngày.',12),(9,'','Bisoprolol fumarat','Suy tim cấp, block nhĩ thất độ II-III, nhịp chậm, hen phế quản nặng.','Đức','2026-07-30 20:26:04.000000','Điều trị tăng huyết áp, đau thắt ngực, suy tim mạn ổn định.','Viên nén bao phim','ETC',NULL,'Merck',15,NULL,NULL,10,'Concor 5mg',NULL,'Hộp 3 vỉ x 10 viên',45000,'VN-17135-13','Mệt mỏi, chóng mặt, lạnh đầu chi.','concor-5mg','5mg','Vỉ','Theo chỉ định của bác sĩ. Uống buổi sáng.',12),(10,'','Losartan kali','Phụ nữ có thai. Quá mẫn với losartan.','Việt Nam','2026-07-30 20:27:04.000000','Điều trị tăng huyết áp, bảo vệ thận ở bệnh nhân đái tháo đường type 2.','Viên nén bao phim','ETC',NULL,'Pymepharco',5,NULL,NULL,10,'Losartan 50mg',NULL,'Hộp 3 vỉ x 10 viên',45000,'VD-26814-17','Chóng mặt, tăng kali máu.','losartan-50mg','50mg','Hộp','Theo chỉ định của bác sĩ.',12),(11,'','Diosmectit','Quá mẫn với thành phần thuốc.','Pháp','2026-07-30 20:28:04.000000','Điều trị tiêu chảy cấp và mạn ở trẻ em và người lớn; giảm đau do viêm thực quản, dạ dày.','Bột pha hỗn dịch uống','OTC',NULL,'Ipsen',5,NULL,NULL,10,'Smecta hương cam',125000,'Hộp 30 gói',115000,'VN-20138-16','Táo bón (hiếm).','smecta-huong-cam','3g','Hộp','Người lớn: 3 gói/ngày, pha trong nửa cốc nước.',4),(12,'','Berberin clorid','Phụ nữ có thai.','Việt Nam','2026-07-30 20:29:04.000000','Hỗ trợ điều trị tiêu chảy, lỵ trực khuẩn, viêm ruột.','Viên nén bao đường','OTC',NULL,'Mekophar',10,NULL,NULL,10,'Berberin 100mg',NULL,'Lọ 100 viên',16000,'VD-22765-15','Táo bón nhẹ.','berberin-100mg','100mg','Lọ','Người lớn: 4-6 viên/lần x 2 lần/ngày.',4),(13,'','Omeprazol','Quá mẫn với omeprazol.','Việt Nam','2026-07-30 20:30:04.000000','Điều trị loét dạ dày tá tràng, trào ngược dạ dày thực quản.','Viên nang tan trong ruột','ETC',NULL,'DHG Pharma',5,NULL,NULL,10,'Omeprazol 20mg',NULL,'Hộp 2 vỉ x 7 viên',28000,'VD-28765-18','Đau đầu, buồn nôn, tiêu chảy.','omeprazol-20mg','20mg','Hộp','Theo chỉ định của bác sĩ. Uống trước ăn sáng 30 phút.',4),(14,'','Bacillus clausii','Quá mẫn với thành phần thuốc.','Ý','2026-07-30 20:31:04.000000','Phòng và điều trị rối loạn hệ vi khuẩn đường ruột, tiêu chảy do dùng kháng sinh.','Hỗn dịch uống','OTC',NULL,'Sanofi',5,NULL,NULL,10,'Enterogermina 2 tỷ/5ml',180000,'Hộp 20 ống x 5ml',165000,'VN-20720-17','Chưa ghi nhận.','enterogermina-2-ty-5ml','2 tỷ bào tử','Hộp','Người lớn: 2-3 ống/ngày; trẻ em: 1-2 ống/ngày.',4),(15,'','Paracetamol, Phenylephrin','Tăng huyết áp nặng, bệnh mạch vành, cường giáp.','Việt Nam','2026-07-30 20:32:04.000000','Giảm các triệu chứng cảm cúm: sốt, nhức đầu, sổ mũi, nghẹt mũi.','Viên nén','OTC',NULL,'United Pharma',75,NULL,NULL,10,'Decolgen ND',NULL,'Hộp 25 vỉ x 4 viên',5000,'VD-26017-16','Hồi hộp, mất ngủ nhẹ.','decolgen-nd','500mg/10mg','Vỉ','Người lớn: 1 viên mỗi 6 giờ.',5),(16,'','Cao lá thường xuân','Không dung nạp fructose.','Đức','2026-07-30 20:33:04.000000','Điều trị viêm đường hô hấp cấp có kèm ho, ho do viêm phế quản mạn tính.','Siro','OTC',NULL,'Engelhard',5,NULL,NULL,10,'Siro ho Prospan 100ml',95000,'Chai 100ml',89000,'VN-19875-16','Rối loạn tiêu hóa nhẹ.','siro-ho-prospan-100ml','0,7g/100ml','Chai','Người lớn: 5-7,5ml x 3 lần/ngày.',5),(17,'','Acid ascorbic','Sỏi thận oxalat, thiếu G6PD.','Việt Nam','2026-07-30 20:34:04.000000','Bổ sung vitamin C, tăng cường sức đề kháng, chống oxy hóa.','Viên sủi','SUPPLEMENT',NULL,'Bidiphar',10,NULL,NULL,10,'Viên sủi Vitamin C 1000mg',42000,'Tuýp 10 viên',35000,'VD-29012-18','Dùng liều cao có thể gây tiêu chảy.','vien-sui-vitamin-c-1000mg','1000mg','Tuýp','Người lớn: 1 viên/ngày, hòa tan trong nước.',6),(18,'','Calci, Vitamin D3','Tăng canxi máu, sỏi thận.','Việt Nam','2026-07-30 20:35:04.000000','Bổ sung canxi và vitamin D3 cho trẻ em đang lớn, phụ nữ có thai, người cao tuổi.','Dung dịch uống','SUPPLEMENT',NULL,'Sanofi',5,NULL,NULL,10,'Canxi D3 Corbiere',NULL,'Hộp 30 ống x 5ml',110000,'VD-24098-16','Táo bón nhẹ.','canxi-d3-corbiere','500mg/200IU','Hộp','Uống 1-2 ống/ngày.',6),(19,'','Dầu cá (EPA, DHA)','Người đang dùng thuốc chống đông cần hỏi ý kiến bác sĩ.','Úc','2026-07-30 20:36:04.000000','Hỗ trợ tim mạch, não bộ và thị lực. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.','Viên nang mềm','SUPPLEMENT',NULL,'Blackmores',3,NULL,NULL,10,'Omega-3 Fish Oil 1000mg',450000,'Lọ 100 viên',395000,'TPCN 4512/2020','Ợ hơi mùi cá.','omega-3-fish-oil-1000mg','1000mg','Lọ','Uống 1 viên x 1-3 lần/ngày, sau bữa ăn.',7),(20,'','Cao bạch quả','Người đang dùng thuốc chống đông, phụ nữ có thai.','Việt Nam','2026-07-30 20:37:04.000000','Hỗ trợ tăng cường tuần hoàn máu não. Sản phẩm này không phải là thuốc, không có tác dụng thay thế thuốc chữa bệnh.','Viên nén','SUPPLEMENT',NULL,'Traphaco',5,NULL,NULL,10,'Ginkgo Biloba 120mg',NULL,'Hộp 3 vỉ x 10 viên',99000,'TPCN 3321/2021','Đau đầu nhẹ (hiếm).','ginkgo-biloba-120mg','120mg','Hộp','Uống 1 viên x 2 lần/ngày.',7),(21,'',NULL,NULL,'Nhật Bản','2026-07-30 20:38:04.000000','Máy đo huyết áp tự động bắp tay, công nghệ IntelliSense, phát hiện nhịp tim bất thường.',NULL,'DEVICE',NULL,'Omron',2,NULL,NULL,10,'Máy đo huyết áp bắp tay Omron HEM-7121',990000,'Hộp 1 máy',890000,'220001234/PCBB-HN',NULL,'may-do-huyet-ap-bap-tay-omron-hem-7121',NULL,'Cái','Quấn vòng bít ngang tim, ngồi yên 5 phút trước khi đo.',8),(22,'',NULL,NULL,'Thụy Sĩ','2026-07-30 20:39:04.000000','Nhiệt kế điện tử đo ở miệng, nách, hậu môn; cho kết quả sau 60 giây.',NULL,'DEVICE',NULL,'Microlife',5,NULL,NULL,10,'Nhiệt kế điện tử Microlife MT200',NULL,'Hộp 1 cái',75000,'220005678/PCBA-HN',NULL,'nhiet-ke-dien-tu-microlife-mt200',NULL,'Cái','Đặt đầu đo đúng vị trí đến khi có tiếng bíp.',8),(23,'',NULL,NULL,'Việt Nam','2026-07-30 20:40:04.000000','Khẩu trang y tế 4 lớp kháng khuẩn, lọc bụi.',NULL,'DEVICE',NULL,'Nam Anh',20,NULL,NULL,10,'Khẩu trang y tế 4 lớp',45000,'Hộp 50 cái',35000,'220009999/PCBA-HCM',NULL,'khau-trang-y-te-4-lop',NULL,'Hộp','Dùng 1 lần.',8),(24,'',NULL,NULL,'Pháp','2026-07-30 20:41:04.000000','Kem chống nắng phổ rộng, kiểm soát dầu, dành cho da nhạy cảm.','Kem','COSMETIC',NULL,'La Roche-Posay',3,NULL,NULL,10,'Kem chống nắng La Roche-Posay Anthelios SPF50+',530000,'Tuýp 50ml',485000,'123456/21/CBMP-QLD',NULL,'kem-chong-nang-la-roche-posay-anthelios-spf50',NULL,'Tuýp','Thoa trước khi ra nắng 20 phút, thoa lại sau mỗi 2 giờ.',9),(25,'',NULL,NULL,'Canada','2026-07-30 20:42:04.000000','Làm sạch dịu nhẹ, không gây kích ứng, phù hợp da nhạy cảm.','Sữa rửa mặt','COSMETIC',NULL,'Galderma',3,NULL,NULL,10,'Sữa rửa mặt Cetaphil Gentle Skin Cleanser',NULL,'Chai 500ml',345000,'98765/20/CBMP-QLD',NULL,'sua-rua-mat-cetaphil-gentle-skin-cleanser',NULL,'Chai','Dùng 2 lần/ngày.',9),(26,'','Diazepam','Suy hô hấp, nhược cơ, ngưng thở khi ngủ.','Hungary','2026-07-30 20:43:04.000000','Thuốc hướng thần - chỉ bán tại nhà thuốc theo đơn thuốc \"H\" của bác sĩ. Không bán online.','Viên nén','SPECIAL',NULL,'Gedeon Richter',NULL,NULL,NULL,10,'Seduxen 5mg',NULL,'Hộp 10 vỉ x 10 viên',60000,'VN-16582-13','Buồn ngủ, lệ thuộc thuốc.','seduxen-5mg','5mg','Hộp','Theo chỉ định của bác sĩ.',10);
/*!40000 ALTER TABLE `products` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `promotion_items`
--

DROP TABLE IF EXISTS `promotion_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `promotion_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `quantity` int(11) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `promotion_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgjsr563pgeef2e89mt5qxp2xn` (`product_id`),
  KEY `FK5oexus1mhu016dyg9xiyepi23` (`promotion_id`),
  CONSTRAINT `FK5oexus1mhu016dyg9xiyepi23` FOREIGN KEY (`promotion_id`) REFERENCES `promotions` (`id`),
  CONSTRAINT `FKgjsr563pgeef2e89mt5qxp2xn` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `promotion_items`
--

LOCK TABLES `promotion_items` WRITE;
/*!40000 ALTER TABLE `promotion_items` DISABLE KEYS */;
INSERT INTO `promotion_items` VALUES (1,1,17,2),(2,1,18,2);
/*!40000 ALTER TABLE `promotion_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `promotions`
--

DROP TABLE IF EXISTS `promotions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `promotions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `buy_quantity` int(11) DEFAULT NULL,
  `combo_discount` bigint(20) DEFAULT NULL,
  `end_at` datetime(6) NOT NULL,
  `gift_quantity` int(11) DEFAULT NULL,
  `name` varchar(150) NOT NULL,
  `quantity_limit` int(11) DEFAULT NULL,
  `sale_price` bigint(20) DEFAULT NULL,
  `sold_count` int(11) NOT NULL,
  `start_at` datetime(6) NOT NULL,
  `type` varchar(20) NOT NULL,
  `gift_product_id` bigint(20) DEFAULT NULL,
  `product_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKsgpk9ngabo55f8ek879p5891` (`gift_product_id`),
  KEY `FK5ukm0jhih3cbin6dhkppos7ot` (`product_id`),
  CONSTRAINT `FK5ukm0jhih3cbin6dhkppos7ot` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKsgpk9ngabo55f8ek879p5891` FOREIGN KEY (`gift_product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `promotions`
--

LOCK TABLES `promotions` WRITE;
/*!40000 ALTER TABLE `promotions` DISABLE KEYS */;
INSERT INTO `promotions` VALUES (1,'',NULL,NULL,'2026-09-30 23:59:04.000000',NULL,'Flash sale Vitamin C tăng đề kháng',50,24500,12,'2026-09-28 18:18:04.000000','FLASH_SALE',NULL,17),(2,'',NULL,20000,'2026-10-28 20:18:04.000000',NULL,'Combo tăng đề kháng: Vitamin C + Canxi D3',NULL,NULL,0,'2026-09-25 20:18:04.000000','COMBO',NULL,NULL),(3,'',2,NULL,'2026-10-18 20:18:04.000000',1,'Mua 2 Omega-3 tặng khẩu trang',NULL,NULL,0,'2026-09-27 20:18:04.000000','GIFT',23,19);
/*!40000 ALTER TABLE `promotions` ENABLE KEYS */;
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
INSERT INTO `receipt_items` VALUES (1,'L24001A','2027-11-02',9000,'2025-12-02',600,1,1),(2,'L25001B','2028-08-28',9000,'2026-06-20',450,1,1),(3,'L24002X','2026-11-12',9000,'2024-10-28',100,2,2),(4,'L25002B','2028-09-02',9000,'2026-06-20',220,2,2),(5,'L24003A','2027-11-22',30240,'2025-12-02',80,3,3),(6,'L25003B','2028-09-07',30240,'2026-06-20',80,3,3),(7,'L24004A','2027-12-02',5040,'2025-12-02',1000,4,4),(8,'L25004B','2028-09-12',5040,'2026-06-20',300,4,4),(9,'L24005A','2027-12-12',79200,'2025-12-02',240,5,5),(10,'L25005B','2028-09-17',79200,'2026-06-20',110,5,5),(11,'L24006A','2027-12-22',6480,'2025-12-02',400,6,6),(12,'L25006B','2028-09-22',6480,'2026-06-20',800,6,6),(13,'L24007A','2028-01-01',176400,'2025-12-02',60,7,7),(14,'L25007B','2028-09-27',176400,'2026-06-20',30,7,7),(15,'L24008A','2028-01-11',7920,'2025-12-02',240,8,8),(16,'L25008B','2028-10-02',7920,'2026-06-20',165,8,8),(17,'L24009A','2028-01-21',32400,'2025-12-02',300,9,9),(18,'L25009B','2028-10-07',32400,'2026-06-20',240,9,9),(19,'L24010A','2028-01-31',32400,'2025-12-02',120,10,10),(20,'L25010B','2028-10-12',32400,'2026-06-20',30,10,10),(21,'L24011A','2028-02-10',82800,'2025-12-02',40,11,11),(22,'L25011B','2028-10-17',82800,'2026-06-20',55,11,11),(23,'L24012A','2028-02-20',11520,'2025-12-02',60,12,12),(24,'L25012B','2028-10-22',11520,'2026-06-20',80,12,12),(25,'L23012Z','2026-09-18',11520,'2024-07-20',12,12,12),(26,'L24013A','2028-03-01',20160,'2025-12-02',80,13,13),(27,'L25013B','2028-10-27',20160,'2026-06-20',30,13,13),(28,'L24014A','2028-03-11',118800,'2025-12-02',100,14,14),(29,'L25014B','2028-11-01',118800,'2026-06-20',55,14,14),(30,'L24015A','2028-03-21',3600,'2025-12-02',3000,15,15),(31,'L25015B','2028-11-06',3600,'2026-06-20',2000,15,15),(32,'L24016A','2028-03-31',64080,'2025-12-02',40,16,16),(33,'L25016B','2028-11-11',64080,'2026-06-20',30,16,16),(34,'L24017A','2028-04-10',25200,'2025-12-02',60,17,17),(35,'L25017B','2028-11-16',25200,'2026-06-20',55,17,17),(36,'L24018A','2028-04-20',79200,'2025-12-02',80,18,18),(37,'L25018B','2028-11-21',79200,'2026-06-20',80,18,18),(38,'L25019A','2028-02-10',284400,'2026-08-09',6,19,19),(39,'L24020A','2028-05-10',71280,'2025-12-02',120,20,20),(40,'L25020B','2028-12-01',71280,'2026-06-20',55,20,20),(41,'L24021A','2028-05-20',640800,'2025-12-02',40,21,21),(42,'L25021B','2028-12-06',640800,'2026-06-20',80,21,21),(43,'L24022A','2028-05-30',54000,'2025-12-02',60,22,22),(44,'L25022B','2028-12-11',54000,'2026-06-20',30,22,22),(45,'L24023A','2028-06-09',25200,'2025-12-02',80,23,23),(46,'L25023B','2028-12-16',25200,'2026-06-20',55,23,23),(47,'L24024A','2028-06-19',349200,'2025-12-02',100,24,24),(48,'L25024B','2028-12-21',349200,'2026-06-20',80,24,24),(49,'L24025A','2028-06-29',248400,'2025-12-02',120,25,25),(50,'L25025B','2028-12-26',248400,'2026-06-20',30,25,25),(51,'L24026A','2028-07-09',43200,'2025-12-02',40,26,26),(52,'L25026B','2028-12-31',43200,'2026-06-20',55,26,26),(53,'L26019C','2028-09-17',285000,'2026-09-08',50,19,27);
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
  `status` varchar(20) NOT NULL,
  `approved_by_id` bigint(20) DEFAULT NULL,
  `created_by_id` bigint(20) DEFAULT NULL,
  `supplier_id` bigint(20) DEFAULT NULL,
  `warehouse_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpalhutkagd8lnbma35aqr048m` (`code`),
  KEY `FKowwsyfvx1k1wp3q8k3odg67fi` (`approved_by_id`),
  KEY `FKsn6hs4rk2wmjfowf6lairrhb8` (`created_by_id`),
  KEY `FK4ksphcrrl2epyxvdqwo0gat9d` (`supplier_id`),
  KEY `FKtpm8me8cng0vjgj5s7ojcd0n0` (`warehouse_id`),
  CONSTRAINT `FK4ksphcrrl2epyxvdqwo0gat9d` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`),
  CONSTRAINT `FKowwsyfvx1k1wp3q8k3odg67fi` FOREIGN KEY (`approved_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsn6hs4rk2wmjfowf6lairrhb8` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKtpm8me8cng0vjgj5s7ojcd0n0` FOREIGN KEY (`warehouse_id`) REFERENCES `warehouses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `receipts`
--

LOCK TABLES `receipts` WRITE;
/*!40000 ALTER TABLE `receipts` DISABLE KEYS */;
INSERT INTO `receipts` VALUES (1,'2026-07-30 20:18:04.000000','PN-INIT-001','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(2,'2026-07-30 20:18:04.000000','PN-INIT-002','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(3,'2026-07-30 20:18:04.000000','PN-INIT-003','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(4,'2026-07-30 20:18:04.000000','PN-INIT-004','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(5,'2026-07-30 20:18:04.000000','PN-INIT-005','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(6,'2026-07-30 20:18:04.000000','PN-INIT-006','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(7,'2026-07-30 20:18:04.000000','PN-INIT-007','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(8,'2026-07-30 20:18:04.000000','PN-INIT-008','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(9,'2026-07-30 20:18:04.000000','PN-INIT-009','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(10,'2026-07-30 20:18:04.000000','PN-INIT-010','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(11,'2026-07-30 20:18:04.000000','PN-INIT-011','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(12,'2026-07-30 20:18:04.000000','PN-INIT-012','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(13,'2026-07-30 20:18:04.000000','PN-INIT-013','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(14,'2026-07-30 20:18:04.000000','PN-INIT-014','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(15,'2026-07-30 20:18:04.000000','PN-INIT-015','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(16,'2026-07-30 20:18:04.000000','PN-INIT-016','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(17,'2026-07-30 20:18:04.000000','PN-INIT-017','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(18,'2026-07-30 20:18:04.000000','PN-INIT-018','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(19,'2026-07-30 20:18:04.000000','PN-INIT-019','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(20,'2026-07-30 20:18:04.000000','PN-INIT-020','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(21,'2026-07-30 20:18:04.000000','PN-INIT-021','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(22,'2026-07-30 20:18:04.000000','PN-INIT-022','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(23,'2026-07-30 20:18:04.000000','PN-INIT-023','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(24,'2026-07-30 20:18:04.000000','PN-INIT-024','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(25,'2026-07-30 20:18:04.000000','PN-INIT-025','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,1,NULL),(26,'2026-07-30 20:18:04.000000','PN-INIT-026','2026-07-30 20:18:04.000000','Nhập hàng đầu kỳ','APPROVED',1,2,2,NULL),(27,NULL,'PN-DEMO-CHO-DUYET','2026-09-28 20:18:05.000000','Bổ sung hàng Omega-3 sắp hết','PENDING',NULL,2,2,NULL);
/*!40000 ALTER TABLE `receipts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `reminders`
--

DROP TABLE IF EXISTS `reminders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `reminders` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_date` date DEFAULT NULL,
  `last_fired_at` datetime(6) DEFAULT NULL,
  `note` varchar(300) DEFAULT NULL,
  `remind_date` date DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `times` varchar(100) DEFAULT NULL,
  `title` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `product_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKifofbixh5rchuv5hm0eacvuck` (`product_id`),
  KEY `FKgibc0ij0e4s7bkldn4xybaanb` (`user_id`),
  CONSTRAINT `FKgibc0ij0e4s7bkldn4xybaanb` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKifofbixh5rchuv5hm0eacvuck` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `reminders`
--

LOCK TABLES `reminders` WRITE;
/*!40000 ALTER TABLE `reminders` DISABLE KEYS */;
/*!40000 ALTER TABLE `reminders` ENABLE KEYS */;
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
INSERT INTO `reviews` VALUES (1,'Thuốc giảm đau nhanh, giao hàng nhanh.','2026-09-28 20:18:06.000000','\0',5,1,7),(2,'Dùng ổn, đóng gói cẩn thận.','2026-09-28 20:18:06.000000','\0',4,11,8);
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
-- Table structure for table `shipping_zones`
--

DROP TABLE IF EXISTS `shipping_zones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `shipping_zones` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `eta` varchar(50) DEFAULT NULL,
  `fee` bigint(20) NOT NULL,
  `free_threshold` bigint(20) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `provinces` varchar(1000) NOT NULL,
  `sort_order` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `shipping_zones`
--

LOCK TABLES `shipping_zones` WRITE;
/*!40000 ALTER TABLE `shipping_zones` DISABLE KEYS */;
INSERT INTO `shipping_zones` VALUES (1,'','2 - 4 giờ',15000,300000,'Nội thành Hà Nội','Hà Nội',1),(2,'','1 - 2 ngày',25000,500000,'Miền Bắc','Hải Phòng, Tuyên Quang, Cao Bằng, Lai Châu, Lào Cai, Thái Nguyên, Điện Biên, Lạng Sơn, Sơn La, Phú Thọ, Bắc Ninh, Quảng Ninh, Hưng Yên, Ninh Bình',2),(3,'','2 - 3 ngày',30000,500000,'Miền Trung - Tây Nguyên','Thanh Hóa, Nghệ An, Hà Tĩnh, Quảng Trị, Huế, Đà Nẵng, Quảng Ngãi, Gia Lai, Khánh Hòa, Lâm Đồng, Đắk Lắk',3),(4,'','2 - 4 ngày',30000,500000,'Miền Nam','TP. Hồ Chí Minh, Đồng Nai, Tây Ninh, Vĩnh Long, Đồng Tháp, Cà Mau, An Giang, Cần Thơ',4);
/*!40000 ALTER TABLE `shipping_zones` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `staff_roles`
--

DROP TABLE IF EXISTS `staff_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `staff_roles` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `description` varchar(300) DEFAULT NULL,
  `name` varchar(80) NOT NULL,
  `permissions` varchar(300) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKegtydy8obpfjtyrcqe2972dhe` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `staff_roles`
--

LOCK TABLES `staff_roles` WRITE;
/*!40000 ALTER TABLE `staff_roles` DISABLE KEYS */;
INSERT INTO `staff_roles` VALUES (1,'Dược sĩ phụ trách chuyên môn / quản lý nhà thuốc - toàn quyền nghiệp vụ','Dược sĩ quản lý','RX_REVIEW,ORDER,CONSULT,INVENTORY,APPROVE_STOCK,POS,REFUND,CONTENT'),(2,'Duyệt đơn thuốc, tư vấn, xử lý đơn và bán tại quầy','Dược sĩ','RX_REVIEW,ORDER,CONSULT,POS'),(3,'Nhập hàng, soạn hàng, kiểm kê, chuyển kho','Nhân viên kho','ORDER,INVENTORY'),(4,'Chat, hỏi đáp, yêu cầu gọi lại, theo dõi đơn','Nhân viên CSKH','ORDER,CONSULT'),(5,'Bài viết sức khỏe, thông tin sản phẩm, kiểm duyệt đánh giá','Biên tập viên','CONTENT');
/*!40000 ALTER TABLE `staff_roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `static_pages`
--

DROP TABLE IF EXISTS `static_pages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `static_pages` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `content` longtext DEFAULT NULL,
  `meta_description` varchar(300) DEFAULT NULL,
  `published` bit(1) NOT NULL,
  `show_in_footer` bit(1) NOT NULL,
  `slug` varchar(120) NOT NULL,
  `sort_order` int(11) NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpqog76htvlwacfsbcdtgv0u1u` (`slug`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `static_pages`
--

LOCK TABLES `static_pages` WRITE;
/*!40000 ALTER TABLE `static_pages` DISABLE KEYS */;
INSERT INTO `static_pages` VALUES (1,'## Điều kiện đổi trả\nKhách hàng được đổi/trả trong 7 ngày kể từ khi nhận hàng khi sản phẩm bị lỗi từ nhà sản xuất, giao sai sản phẩm, hư hỏng do vận chuyển hoặc còn nguyên tem niêm phong.\n\n## Không áp dụng đổi trả\n- Thuốc kê đơn (trừ trường hợp lỗi từ nhà thuốc)\n- Sản phẩm đã mở niêm phong, đã sử dụng\n- Sản phẩm cần bảo quản lạnh\n\n## Hoàn tiền\nSau khi nhà thuốc duyệt yêu cầu, tiền được hoàn về tài khoản / ví của khách trong 3 - 7 ngày làm việc. Điểm tích lũy đã dùng được hoàn lại.',NULL,'','','chinh-sach-doi-tra',1,'Chính sách đổi trả & hoàn tiền','2026-09-28 20:18:04.000000'),(2,'## Phạm vi & phí giao hàng\nNhà thuốc giao hàng toàn quốc. Phí ship tính theo khu vực, miễn phí cho đơn đạt ngưỡng (xem chi tiết khi thanh toán).\n\n## Thời gian\n- Nội thành Hà Nội: 2 - 4 giờ\n- Các tỉnh miền Bắc: 1 - 2 ngày\n- Miền Trung, miền Nam: 2 - 4 ngày\n\nĐơn có thuốc kê đơn chỉ được giao sau khi dược sĩ duyệt đơn thuốc.',NULL,'','','chinh-sach-giao-hang',2,'Chính sách giao hàng','2026-09-28 20:18:04.000000'),(3,'## Thông tin thu thập\nHọ tên, số điện thoại, địa chỉ giao hàng, hồ sơ sức khỏe (dị ứng, bệnh nền) và ảnh đơn thuốc khách hàng cung cấp.\n\n## Mục đích sử dụng\nXử lý đơn hàng, tư vấn dùng thuốc an toàn, chăm sóc khách hàng. Ảnh đơn thuốc và hồ sơ sức khỏe chỉ dược sĩ được xem.\n\n## Cam kết\nKhông chia sẻ thông tin cho bên thứ ba trừ khi pháp luật yêu cầu. Khách hàng có thể yêu cầu xem, sửa hoặc xóa dữ liệu cá nhân.',NULL,'','','chinh-sach-bao-mat',3,'Chính sách bảo mật thông tin','2026-09-28 20:18:04.000000'),(4,'Khi sử dụng website, khách hàng đồng ý cung cấp thông tin chính xác và sử dụng thuốc theo hướng dẫn của bác sĩ, dược sĩ.\n\nThông tin trên website chỉ mang tính tham khảo, không thay thế chẩn đoán và điều trị của bác sĩ.\n\nThuốc kiểm soát đặc biệt không được bán online theo quy định của pháp luật.',NULL,'','','dieu-khoan-su-dung',4,'Điều khoản sử dụng','2026-09-28 20:18:04.000000');
/*!40000 ALTER TABLE `static_pages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_adjustments`
--

DROP TABLE IF EXISTS `stock_adjustments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `stock_adjustments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `quantity` int(11) NOT NULL,
  `reason` varchar(300) DEFAULT NULL,
  `reject_reason` varchar(300) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `type` varchar(20) DEFAULT NULL,
  `approved_by_id` bigint(20) DEFAULT NULL,
  `batch_id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK1rg7hwuotawvnpeggblyi8y92` (`approved_by_id`),
  KEY `FK7f6a15gyagmqmfqlf140ery7y` (`batch_id`),
  KEY `FKp03oubo6sxk9v82es72rxvvsg` (`user_id`),
  CONSTRAINT `FK1rg7hwuotawvnpeggblyi8y92` FOREIGN KEY (`approved_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK7f6a15gyagmqmfqlf140ery7y` FOREIGN KEY (`batch_id`) REFERENCES `batches` (`id`),
  CONSTRAINT `FKp03oubo6sxk9v82es72rxvvsg` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_adjustments`
--

LOCK TABLES `stock_adjustments` WRITE;
/*!40000 ALTER TABLE `stock_adjustments` DISABLE KEYS */;
INSERT INTO `stock_adjustments` VALUES (1,NULL,'2026-09-28 20:18:06.000000',-3,'Hộp bị móp, ẩm - đề nghị hủy',NULL,'PENDING','WRITE_OFF',NULL,3,4);
/*!40000 ALTER TABLE `stock_adjustments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `stock_subscriptions`
--

DROP TABLE IF EXISTS `stock_subscriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `stock_subscriptions` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `notified` bit(1) NOT NULL,
  `notified_at` datetime(6) DEFAULT NULL,
  `product_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnr3h9h7mpc9t89oncqdfq696r` (`product_id`),
  KEY `FKb2t9xs2v2flg9mlaoplbl02ta` (`user_id`),
  CONSTRAINT `FKb2t9xs2v2flg9mlaoplbl02ta` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKnr3h9h7mpc9t89oncqdfq696r` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `stock_subscriptions`
--

LOCK TABLES `stock_subscriptions` WRITE;
/*!40000 ALTER TABLE `stock_subscriptions` DISABLE KEYS */;
/*!40000 ALTER TABLE `stock_subscriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `suggested_cart_items`
--

DROP TABLE IF EXISTS `suggested_cart_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `suggested_cart_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `price` bigint(20) NOT NULL,
  `quantity` int(11) NOT NULL,
  `unit_id` bigint(20) DEFAULT NULL,
  `unit_name` varchar(30) DEFAULT NULL,
  `cart_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgilfqhgnu8uihjvtp4ltw7i48` (`cart_id`),
  KEY `FKfofe9c7uxg1dfkpd8gk7sb9uc` (`product_id`),
  CONSTRAINT `FKfofe9c7uxg1dfkpd8gk7sb9uc` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKgilfqhgnu8uihjvtp4ltw7i48` FOREIGN KEY (`cart_id`) REFERENCES `suggested_carts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suggested_cart_items`
--

LOCK TABLES `suggested_cart_items` WRITE;
/*!40000 ALTER TABLE `suggested_cart_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `suggested_cart_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `suggested_carts`
--

DROP TABLE IF EXISTS `suggested_carts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `suggested_carts` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `conversation_id` bigint(20) NOT NULL,
  `pharmacist_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKf9gs5ijnpupj7avlm1nm8gdo0` (`conversation_id`),
  KEY `FK3wji6ydqk32n4rwjdln9k5p2p` (`pharmacist_id`),
  CONSTRAINT `FK3wji6ydqk32n4rwjdln9k5p2p` FOREIGN KEY (`pharmacist_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKf9gs5ijnpupj7avlm1nm8gdo0` FOREIGN KEY (`conversation_id`) REFERENCES `conversations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suggested_carts`
--

LOCK TABLES `suggested_carts` WRITE;
/*!40000 ALTER TABLE `suggested_carts` DISABLE KEYS */;
/*!40000 ALTER TABLE `suggested_carts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `supplier_payments`
--

DROP TABLE IF EXISTS `supplier_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `supplier_payments` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `amount` bigint(20) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `method` varchar(30) DEFAULT NULL,
  `note` varchar(300) DEFAULT NULL,
  `paid_date` date NOT NULL,
  `created_by_id` bigint(20) DEFAULT NULL,
  `supplier_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbdmtfhscaiiy8ns54uwaq40yj` (`created_by_id`),
  KEY `FKdwv3fhnvnbuvd6h2ri8iuiw2q` (`supplier_id`),
  CONSTRAINT `FKbdmtfhscaiiy8ns54uwaq40yj` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKdwv3fhnvnbuvd6h2ri8iuiw2q` FOREIGN KEY (`supplier_id`) REFERENCES `suppliers` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `supplier_payments`
--

LOCK TABLES `supplier_payments` WRITE;
/*!40000 ALTER TABLE `supplier_payments` DISABLE KEYS */;
INSERT INTO `supplier_payments` VALUES (1,5000000,'2026-09-28 20:18:06.000000','Chuyển khoản','Thanh toán đợt 1','2026-09-08',1,1);
/*!40000 ALTER TABLE `supplier_payments` ENABLE KEYS */;
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
  `payment_term_days` int(11) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `tax_code` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suppliers`
--

LOCK TABLES `suppliers` WRITE;
/*!40000 ALTER TABLE `suppliers` DISABLE KEYS */;
INSERT INTO `suppliers` VALUES (1,'160 Tôn Đức Thắng, Hà Nội','sales@pharbaco.vn','Công ty CP Dược phẩm Trung ương 1',30,'02438252000','0100108536'),(2,'KCN Tân Tạo, TP.HCM','order@zuellig.vn','Công ty TNHH Phân phối Zuellig Pharma',45,'02838123456',NULL);
/*!40000 ALTER TABLE `suppliers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `transfer_items`
--

DROP TABLE IF EXISTS `transfer_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `transfer_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `quantity` int(11) NOT NULL,
  `slip_id` bigint(20) NOT NULL,
  `source_batch_id` bigint(20) NOT NULL,
  `target_batch_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5l9lnlryaa260g4xc4j2k50yo` (`slip_id`),
  KEY `FKawx4ken1qng8oosupgfnj5yhe` (`source_batch_id`),
  KEY `FKpaira7ef02xjo2o3s39pn9sjq` (`target_batch_id`),
  CONSTRAINT `FK5l9lnlryaa260g4xc4j2k50yo` FOREIGN KEY (`slip_id`) REFERENCES `transfer_slips` (`id`),
  CONSTRAINT `FKawx4ken1qng8oosupgfnj5yhe` FOREIGN KEY (`source_batch_id`) REFERENCES `batches` (`id`),
  CONSTRAINT `FKpaira7ef02xjo2o3s39pn9sjq` FOREIGN KEY (`target_batch_id`) REFERENCES `batches` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `transfer_items`
--

LOCK TABLES `transfer_items` WRITE;
/*!40000 ALTER TABLE `transfer_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `transfer_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `transfer_slips`
--

DROP TABLE IF EXISTS `transfer_slips`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `transfer_slips` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `code` varchar(30) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `created_by_id` bigint(20) DEFAULT NULL,
  `from_warehouse_id` bigint(20) NOT NULL,
  `to_warehouse_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKku58hhp7j56261kyv3y0gajc` (`code`),
  KEY `FK6bmm5n5po6g0f0q4sc4jvvxp0` (`created_by_id`),
  KEY `FK1dhiiggw0i7haix8w0604lkna` (`from_warehouse_id`),
  KEY `FKewb3daa3xoaf7dw8wa3ccugc5` (`to_warehouse_id`),
  CONSTRAINT `FK1dhiiggw0i7haix8w0604lkna` FOREIGN KEY (`from_warehouse_id`) REFERENCES `warehouses` (`id`),
  CONSTRAINT `FK6bmm5n5po6g0f0q4sc4jvvxp0` FOREIGN KEY (`created_by_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKewb3daa3xoaf7dw8wa3ccugc5` FOREIGN KEY (`to_warehouse_id`) REFERENCES `warehouses` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `transfer_slips`
--

LOCK TABLES `transfer_slips` WRITE;
/*!40000 ALTER TABLE `transfer_slips` DISABLE KEYS */;
/*!40000 ALTER TABLE `transfer_slips` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_vouchers`
--

DROP TABLE IF EXISTS `user_vouchers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_vouchers` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `saved_at` datetime(6) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `voucher_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbkm2rdj5iur98b7u0ve192lq8` (`user_id`,`voucher_id`),
  KEY `FK40ig7khk2v79rbqaj98mf1g2q` (`voucher_id`),
  CONSTRAINT `FK40ig7khk2v79rbqaj98mf1g2q` FOREIGN KEY (`voucher_id`) REFERENCES `vouchers` (`id`),
  CONSTRAINT `FK90ahc2var0yrghyxr9tapdokg` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_vouchers`
--

LOCK TABLES `user_vouchers` WRITE;
/*!40000 ALTER TABLE `user_vouchers` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_vouchers` ENABLE KEYS */;
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
  `degree` varchar(200) DEFAULT NULL,
  `email` varchar(150) DEFAULT NULL,
  `full_name` varchar(100) NOT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `license_no` varchar(50) DEFAULT NULL,
  `locked` bit(1) NOT NULL,
  `password_hash` varchar(255) NOT NULL,
  `permissions` varchar(200) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `points` int(11) NOT NULL,
  `pregnancy` bit(1) NOT NULL,
  `role` varchar(20) NOT NULL,
  `shift` varchar(100) DEFAULT NULL,
  `staff_role_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  KEY `FKaas2ykv1u8dl8dcif961x4yrf` (`staff_role_id`),
  CONSTRAINT `FKaas2ykv1u8dl8dcif961x4yrf` FOREIGN KEY (`staff_role_id`) REFERENCES `staff_roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,NULL,NULL,NULL,'2026-06-30 20:18:04.000000',NULL,'admin@hieuthuoc.vn','Quản trị viên',NULL,NULL,NULL,'\0','$2a$10$3xzMakNw09ptHEnobGCuIuPrhWhX6B7/u3uvP3SQkO7RJBFXa9ZpO',NULL,'0901000001',0,'\0','ADMIN',NULL,NULL),(2,NULL,NULL,NULL,'2026-06-30 20:18:04.000000','Dược sĩ đại học - ĐH Dược Hà Nội','duocsi@hieuthuoc.vn','DS. Nguyễn Thị Lan',NULL,NULL,'012345/HNO-CCHND','\0','$2a$10$1yXWQF197/6fVQRrW9TDl.g8Sg0aKk7emNWGAxAzPbdcWglhLna6i',NULL,'0901000002',0,'\0','PHARMACIST','Ca sáng 7h - 15h',1),(3,NULL,NULL,NULL,'2026-06-30 20:18:04.000000','Dược sĩ cao đẳng','duocsi2@hieuthuoc.vn','DS. Phạm Quốc Huy',NULL,NULL,'023456/HNO-CCHND','\0','$2a$10$RVeRXgdHY/U8dtbVUW7Q1.ywm7JSEtdSQclWFCaq0IZV.tVIanIPK','CONTENT','0901000003',0,'\0','PHARMACIST','Ca chiều 14h - 22h',2),(4,NULL,NULL,NULL,'2026-06-30 20:18:04.000000',NULL,'kho@hieuthuoc.vn','Vũ Văn Kho',NULL,NULL,NULL,'\0','$2a$10$0hhJq/UoWHRVXBIpcbR0aulTfr8LGuXpEd7NSasbvv0t1xgQyyOaW',NULL,'0901000004',0,'\0','PHARMACIST','Hành chính 8h - 17h',3),(5,NULL,NULL,NULL,'2026-06-30 20:18:04.000000',NULL,'cskh@hieuthuoc.vn','Đỗ Thu Hà',NULL,NULL,NULL,'\0','$2a$10$.dbLiiCA8JYkzKqIxtyLkOaMmvvZ.NehdJhZ/fizmxtoDDQPf/qbG',NULL,'0901000005',0,'\0','PHARMACIST','Ca chiều 14h - 22h',4),(6,'Dị ứng Aspirin','1990-05-12','Viêm dạ dày','2026-06-30 20:18:04.000000',NULL,'khachhang@gmail.com','Trần Văn An','Nam',NULL,NULL,'\0','$2a$10$Hgi0/DTSSycWigU.Wd9Do.XbRmabI3qh.Jg1RS1q7USKPdds44T4e',NULL,'0912345678',755,'\0','CUSTOMER',NULL,NULL),(7,NULL,NULL,NULL,'2026-06-30 20:18:04.000000',NULL,'binh@gmail.com','Lê Thị Bình','Nữ',NULL,NULL,'\0','$2a$10$TbdFwOKGjkRXtDcrzBRJX.zZAYPNH0u1Y8XJIQXrNG57XSTbeIYCS',NULL,'0987654321',936,'\0','CUSTOMER',NULL,NULL),(8,NULL,NULL,'Tăng huyết áp','2026-06-30 20:18:04.000000',NULL,'chau@gmail.com','Hoàng Minh Châu',NULL,NULL,NULL,'\0','$2a$10$DXxJ5W7OQFxK.1q/aIznxeLlb/xLZoKs.8sWuUxV.X45O39JgNccG',NULL,'0934567890',909,'\0','CUSTOMER',NULL,NULL);
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
  `min_tier` varchar(20) DEFAULT NULL,
  `new_customer_only` bit(1) DEFAULT NULL,
  `per_user_limit` int(11) DEFAULT NULL,
  `show_in_wallet` bit(1) DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `type` varchar(10) NOT NULL,
  `usage_limit` int(11) DEFAULT NULL,
  `used_count` int(11) NOT NULL,
  `discount_value` bigint(20) DEFAULT NULL,
  `category_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK30ftp2biebbvpik8e49wlmady` (`code`),
  KEY `FKde2rrgktr2ehhkdosv4uotdcg` (`category_id`),
  CONSTRAINT `FKde2rrgktr2ehhkdosv4uotdcg` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `vouchers`
--

LOCK TABLES `vouchers` WRITE;
/*!40000 ALTER TABLE `vouchers` DISABLE KEYS */;
INSERT INTO `vouchers` VALUES (1,'','WELCOME10','Giảm 10% cho đơn từ 100.000đ (tối đa 50.000đ)','2027-03-27',50000,100000,NULL,NULL,NULL,'\0','2026-08-29','PERCENT',1000,0,10,NULL),(2,'','GIAM30K','Giảm 30.000đ cho đơn từ 300.000đ','2026-11-27',NULL,300000,NULL,NULL,NULL,'','2026-09-18','FIXED',200,0,30000,NULL),(3,'','FREESHIP20','Giảm 20.000đ cho đơn từ 150.000đ','2026-12-27',NULL,150000,NULL,NULL,NULL,'','2026-09-23','FIXED',500,0,20000,NULL),(4,'','VITAMIN15','Giảm 15% cho đơn từ 200.000đ (tối đa 40.000đ)','2026-11-12',40000,200000,NULL,NULL,NULL,'','2026-09-23','PERCENT',300,0,15,6),(5,'','VIPVANG','Thành viên Vàng trở lên: giảm 50.000đ cho đơn từ 400.000đ','2026-12-27',NULL,400000,'VANG',NULL,2,'','2026-09-27','FIXED',NULL,0,50000,NULL),(6,'','MOIDEN25K','Khách mua lần đầu: giảm 25.000đ cho đơn từ 150.000đ','2027-01-26',NULL,150000,NULL,'',1,'','2026-09-27','FIXED',NULL,0,25000,NULL);
/*!40000 ALTER TABLE `vouchers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `warehouses`
--

DROP TABLE IF EXISTS `warehouses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `warehouses` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `address` varchar(300) DEFAULT NULL,
  `main` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `sellable` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2qm0l82n5ivhyqwmgejxxefm1` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `warehouses`
--

LOCK TABLES `warehouses` WRITE;
/*!40000 ALTER TABLE `warehouses` DISABLE KEYS */;
INSERT INTO `warehouses` VALUES (1,'','123 Nguyễn Trãi, Thanh Xuân, Hà Nội','','Kho chính - Nhà thuốc Thanh Xuân',''),(2,'','KCN Sài Đồng, Long Biên, Hà Nội','\0','Kho dự trữ Long Biên','\0');
/*!40000 ALTER TABLE `warehouses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wishlist_items`
--

DROP TABLE IF EXISTS `wishlist_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `wishlist_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtp53unkks741xiqi6m620i7mx` (`user_id`,`product_id`),
  KEY `FKqxj7lncd242b59fb78rqegyxj` (`product_id`),
  CONSTRAINT `FKmmj2k1i459yu449k3h1vx5abp` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqxj7lncd242b59fb78rqegyxj` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wishlist_items`
--

LOCK TABLES `wishlist_items` WRITE;
/*!40000 ALTER TABLE `wishlist_items` DISABLE KEYS */;
/*!40000 ALTER TABLE `wishlist_items` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-28 20:18:34
