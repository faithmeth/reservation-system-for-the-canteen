# Yemekhane Rezervasyon Sistemi

Kurumsal yemekhaneler icin gelistirilmis tam yiginli bir rezervasyon, odeme ve yonetim uygulamasidir.

Sistem; aylik yemek rezervasyonlarini, coklu ay odeme toplamlarini, tatil gunu yonetimini, otomatik iade kayitlarini, kullanici yonetimini ve yonetici istatistik panelini tek platformda sunar.

## Hizli kurulum

### En kolay yol: Docker Compose

Gereksinim: Docker ve Docker Compose.

```bash
git clone https://github.com/faithmeth/reservation-system-for-the-canteen.git
cd reservation-system-for-the-canteen
cp .env.example .env
docker compose up --build
```

Adresler:

| Servis | URL |
|---|---|
| Frontend | `http://localhost:8081` |
| Backend API | `http://localhost:8080/api` |
| PostgreSQL | `localhost:5432` |

> Docker Compose akisi PostgreSQL kullanir. Gelistirme icin veriler `postgres-data` volume icinde kalici tutulur.

### Lokal gelistirme

Gereksinimler:

| Arac | Surum |
|---|---|
| Java | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ |
| npm | 9+ |

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Lokal gelistirme adresleri:

| Servis | URL |
|---|---|
| Frontend | `http://localhost:5173` |
| Backend API | `http://localhost:8080/api` |
| H2 Console | `http://localhost:8080/h2-console` |

Lokal modda backend varsayilan olarak dosya tabanli H2 veritabani kullanir: `backend/data/yemekhane_db.mv.db`.

## Ortam degiskenleri

Kok dizindeki `.env.example` dosyasini `.env` olarak kopyalayip Docker Compose ayarlarini degistirebilirsiniz.

Onemli degiskenler:

| Degisken | Aciklama |
|---|---|
| `POSTGRES_DB` | Docker PostgreSQL veritabani adi |
| `POSTGRES_USER` | Docker PostgreSQL kullanicisi |
| `POSTGRES_PASSWORD` | Docker PostgreSQL parolasi |
| `ACTIVE_YEAR` | Rezervasyon yili |
| `DAILY_PRICE` | Gunluk yemek ucreti |
| `APP_TIMEZONE` | Uygulama saat dilimi |
| `CORS_ALLOWED_ORIGINS` | Frontend origin listesi |
| `JWT_SECRET` | JWT imzalama anahtari |
| `VITE_API_URL` | Frontend tarafindan kullanilan API adresi |

## Demo kullanicilar

Uygulama ilk calistirildiginda demo kullanicilar otomatik olusturulur.

| Ad Soyad | E-posta | Sifre | Rol |
|---|---|---|---|
| Admin Sistem | `admin@yemekhane.com` | `123456` | Admin |
| Standart Kullanici | `kullanici@yemekhane.com` | `123456` | Kullanici |
| Ahmet Yilmaz | `ahmet@yemekhane.com` | `123456` | Kullanici |
| Ayse Kaya | `ayse@yemekhane.com` | `123456` | Kullanici |
| Mehmet Demir | `mehmet@yemekhane.com` | `123456` | Kullanici |
| Zeynep Celik | `zeynep@yemekhane.com` | `123456` | Kullanici |
| Mustafa Ozturk | `mustafa@yemekhane.com` | `123456` | Kullanici |

## Baslangic verisi yukleme

Haziran, Temmuz ve Agustos 2026 menulerini yuklemek icin backend calisirken:

```powershell
powershell -ExecutionPolicy Bypass -File .\seed_data.ps1
```

> Script demo kullanicilari ve tatilleri tekrar eklemeye calisabilir; mevcut kayitlarda hata alirsa atlayarak devam eder.

## Teknoloji yigini

### Backend

| Teknoloji | Kullanim |
|---|---|
| Java 17 | Temel dil |
| Spring Boot 3.2.3 | Uygulama catisi |
| Spring Web | REST API |
| Spring Data JPA | Veri erisimi |
| Spring Validation | Istek dogrulama |
| Spring Security + JWT | Kimlik dogrulama |
| H2 | Lokal gelistirme veritabani |
| PostgreSQL | Docker/production veritabani |
| Lombok | Boilerplate azaltma |
| Maven | Build araci |

### Frontend

| Teknoloji | Kullanim |
|---|---|
| React 19 | UI kutuphanesi |
| Vite | Build araci ve gelistirme sunucusu |
| React Router DOM | Sayfa yonlendirme |
| TanStack Query | Sunucu durumu yonetimi |
| Axios | HTTP istekleri |
| Lucide React | Ikon kutuphanesi |
| Vanilla CSS | Stil sistemi |

## Proje yapisi

```text
reservation-system-for-the-canteen/
├── backend/                  # Spring Boot backend
│   ├── src/main/java/com/yemekhane/
│   ├── src/main/resources/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/                 # React + Vite frontend
│   ├── src/
│   ├── Dockerfile
│   └── package.json
├── docker-compose.yml
├── .env.example
├── seed_data.ps1
├── README.md
└── .gitignore
```

## API uclari

Tum API uclari `http://localhost:8080/api` altindadir.

### Kullanicilar

| Method | Endpoint | Aciklama |
|---|---|---|
| `POST` | `/users/login` | Giris islemi |
| `POST` | `/users/logout` | Cikis islemi |
| `GET` | `/users` | Tum kullanicilari listele |
| `POST` | `/users` | Yeni kullanici olustur |
| `POST` | `/users/batch` | Toplu kullanici olustur |
| `PUT` | `/users/{id}` | Kullanici guncelle |
| `DELETE` | `/users/{id}` | Kullanici sil |

### Menuler

| Method | Endpoint | Aciklama |
|---|---|---|
| `GET` | `/menus/{yil}/{ay}` | Aya ait menuleri getir |
| `POST` | `/menus` | Yeni menu ekle |
| `POST` | `/menus/batch` | Toplu menu ekle |
| `PUT` | `/menus/{id}` | Menu guncelle |
| `DELETE` | `/menus/{id}` | Menu sil |

### Rezervasyonlar

| Method | Endpoint | Aciklama |
|---|---|---|
| `GET` | `/reservations` | Tum rezervasyonlar |
| `GET` | `/reservations/user/{userId}` | Kullanici rezervasyonlari |
| `POST` | `/reservations/reserve` | Rezervasyon olustur |
| `POST` | `/reservations/bulk` | Coklu ay rezervasyon islemi |
| `PUT` | `/reservations/update/{id}` | Rezervasyon guncelle |

### Tatil gunleri

| Method | Endpoint | Aciklama |
|---|---|---|
| `GET` | `/holidays` | Tatil gunlerini listele |
| `POST` | `/holidays` | Tatil gunu ekle |
| `DELETE` | `/holidays/{id}` | Tatil gunu sil |
| `GET` | `/holidays/refunds` | Tum iade kayitlari |
| `GET` | `/holidays/refunds/user/{userId}` | Kullanici iade kayitlari |
| `PUT` | `/holidays/refunds/{id}/mark-refunded` | Iadeyi odendi isaretle |

### Istatistikler

| Method | Endpoint | Aciklama |
|---|---|---|
| `GET` | `/stats/overview` | Genel istatistikler |
| `GET` | `/stats/monthly-revenue` | Aylik gelir grafigi |

## Notlar

Lokal backend varsayilan ayarlarla ek veritabani kurulumu gerektirmez.

Docker Compose akisi PostgreSQL ile calisir ve `.env` dosyasina baglidir.

Production ortaminda `JWT_SECRET`, `POSTGRES_PASSWORD`, CORS originleri ve cookie secure ayari mutlaka ortam degiskenleriyle guvenli degerlere cekilmelidir.
