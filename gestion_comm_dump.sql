--
-- PostgreSQL database dump
--

-- Dumped from database version 17.5
-- Dumped by pg_dump version 17.5

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_settings; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.app_settings (
    id bigint NOT NULL,
    category character varying(30) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    description character varying(500),
    is_encrypted boolean NOT NULL,
    is_system boolean NOT NULL,
    setting_key character varying(100) NOT NULL,
    sort_order integer,
    updated_at timestamp(6) without time zone,
    setting_value text,
    value_type character varying(20) NOT NULL,
    CONSTRAINT app_settings_category_check CHECK (((category)::text = ANY ((ARRAY['GENERAL'::character varying, 'COMPANY'::character varying, 'EMAIL'::character varying, 'INVOICE'::character varying, 'TAX'::character varying, 'SECURITY'::character varying, 'NOTIFICATION'::character varying, 'SYSTEM'::character varying, 'INTEGRATION'::character varying, 'APPEARANCE'::character varying])::text[]))),
    CONSTRAINT app_settings_value_type_check CHECK (((value_type)::text = ANY ((ARRAY['STRING'::character varying, 'TEXT'::character varying, 'INTEGER'::character varying, 'DECIMAL'::character varying, 'BOOLEAN'::character varying, 'EMAIL'::character varying, 'URL'::character varying, 'PASSWORD'::character varying, 'COLOR'::character varying, 'DATE'::character varying, 'TIME'::character varying, 'JSON'::character varying, 'FILE_PATH'::character varying, 'LIST'::character varying])::text[])))
);


ALTER TABLE public.app_settings OWNER TO postgres;

--
-- Name: app_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.app_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.app_settings_id_seq OWNER TO postgres;

--
-- Name: app_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.app_settings_id_seq OWNED BY public.app_settings.id;


--
-- Name: clients; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.clients (
    id bigint NOT NULL,
    address character varying(200),
    city character varying(50),
    client_type character varying(255),
    company_name character varying(100),
    country character varying(50),
    created_at timestamp(6) without time zone,
    email character varying(255),
    follow_up_date timestamp(6) without time zone,
    mobile_number character varying(255),
    name character varying(100) NOT NULL,
    notes text,
    phone_number character varying(255),
    postal_code character varying(10),
    siret_number character varying(14),
    status character varying(255),
    updated_at timestamp(6) without time zone,
    vat_number character varying(15),
    assigned_user_id bigint,
    CONSTRAINT clients_client_type_check CHECK (((client_type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'COMPANY'::character varying, 'ASSOCIATION'::character varying])::text[]))),
    CONSTRAINT clients_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'PROSPECT'::character varying, 'BLOCKED'::character varying])::text[])))
);


ALTER TABLE public.clients OWNER TO postgres;

--
-- Name: clients_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.clients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.clients_id_seq OWNER TO postgres;

--
-- Name: clients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.clients_id_seq OWNED BY public.clients.id;


--
-- Name: external_payments; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.external_payments (
    id bigint NOT NULL,
    amount numeric(10,2) NOT NULL,
    card_brand character varying(255),
    card_last_four character varying(255),
    card_type character varying(255),
    client_ip character varying(255),
    completed_at timestamp(6) without time zone,
    created_at timestamp(6) without time zone,
    customer_email character varying(255),
    customer_name character varying(255),
    expires_at timestamp(6) without time zone,
    failure_reason text,
    gateway_fee numeric(10,2),
    gateway_payment_intent_id character varying(255),
    gateway_provider character varying(255) NOT NULL,
    gateway_session_id character varying(255),
    gateway_transaction_id character varying(255),
    net_amount numeric(10,2),
    payment_method character varying(255) NOT NULL,
    security_token character varying(255),
    status character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone,
    user_agent text,
    invoice_id bigint NOT NULL,
    CONSTRAINT external_payments_card_type_check CHECK (((card_type)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying, 'PREPAID'::character varying])::text[]))),
    CONSTRAINT external_payments_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['VISA'::character varying, 'MASTERCARD'::character varying, 'PAYPAL'::character varying, 'BANK_TRANSFER'::character varying])::text[]))),
    CONSTRAINT external_payments_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'REFUNDED'::character varying, 'DISPUTED'::character varying])::text[])))
);


ALTER TABLE public.external_payments OWNER TO postgres;

--
-- Name: external_payments_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.external_payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.external_payments_id_seq OWNER TO postgres;

--
-- Name: external_payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.external_payments_id_seq OWNED BY public.external_payments.id;


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


ALTER TABLE public.flyway_schema_history OWNER TO postgres;

--
-- Name: invoice_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.invoice_items (
    id bigint NOT NULL,
    description character varying(200) NOT NULL,
    discount_amount numeric(10,2),
    discount_rate numeric(5,2),
    quantity integer NOT NULL,
    reference character varying(50),
    total_price numeric(10,2),
    total_price_ht numeric(10,2),
    total_vat_amount numeric(10,2),
    unit character varying(50),
    unit_price numeric(10,2) NOT NULL,
    vat_rate numeric(5,2),
    invoice_id bigint NOT NULL,
    CONSTRAINT invoice_items_quantity_check CHECK ((quantity >= 1))
);


ALTER TABLE public.invoice_items OWNER TO postgres;

--
-- Name: invoice_items_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.invoice_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.invoice_items_id_seq OWNER TO postgres;

--
-- Name: invoice_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.invoice_items_id_seq OWNED BY public.invoice_items.id;


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.invoices (
    id bigint NOT NULL,
    billing_address text,
    created_at timestamp(6) without time zone,
    discount_amount numeric(10,2),
    discount_rate numeric(5,2),
    due_date date NOT NULL,
    email_sent boolean,
    email_sent_date timestamp(6) without time zone,
    invoice_date date NOT NULL,
    invoice_number character varying(50) NOT NULL,
    invoice_type character varying(255),
    notes text,
    paid_amount numeric(10,2),
    payment_date date,
    payment_method character varying(255),
    payment_reference character varying(100),
    pdf_path character varying(255),
    shipping_cost numeric(10,2),
    status character varying(255) NOT NULL,
    terms_conditions text,
    total_amount numeric(10,2),
    total_amount_ht numeric(10,2),
    total_vat_amount numeric(10,2),
    updated_at timestamp(6) without time zone,
    order_id bigint NOT NULL,
    CONSTRAINT invoices_invoice_type_check CHECK (((invoice_type)::text = ANY ((ARRAY['STANDARD'::character varying, 'PROFORMA'::character varying, 'CREDIT_NOTE'::character varying, 'DEPOSIT'::character varying])::text[]))),
    CONSTRAINT invoices_payment_method_check CHECK (((payment_method)::text = ANY ((ARRAY['CASH'::character varying, 'CARD'::character varying, 'TRANSFER'::character varying, 'CHECK'::character varying, 'PAYPAL'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT invoices_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'SENT'::character varying, 'PAID'::character varying, 'PARTIAL'::character varying, 'OVERDUE'::character varying, 'CANCELLED'::character varying])::text[])))
);


ALTER TABLE public.invoices OWNER TO postgres;

--
-- Name: invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.invoices_id_seq OWNER TO postgres;

--
-- Name: invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.invoices_id_seq OWNED BY public.invoices.id;


--
-- Name: order_items; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.order_items (
    id bigint NOT NULL,
    discount_amount numeric(10,2),
    discount_rate numeric(5,2),
    quantity integer NOT NULL,
    total_price numeric(10,2),
    total_price_ht numeric(10,2),
    total_vat_amount numeric(10,2),
    unit_price numeric(10,2) NOT NULL,
    vat_rate numeric(5,2),
    order_id bigint NOT NULL,
    product_id bigint NOT NULL,
    description character varying(255),
    CONSTRAINT order_items_quantity_check CHECK ((quantity >= 1))
);


ALTER TABLE public.order_items OWNER TO postgres;

--
-- Name: order_items_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.order_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.order_items_id_seq OWNER TO postgres;

--
-- Name: order_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.order_items_id_seq OWNED BY public.order_items.id;


--
-- Name: orders; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.orders (
    id bigint NOT NULL,
    billing_address text,
    created_at timestamp(6) without time zone,
    delivery_date timestamp(6) without time zone,
    discount_amount numeric(10,2),
    discount_rate numeric(5,2),
    expected_delivery_date timestamp(6) without time zone,
    internal_notes text,
    notes text,
    order_date timestamp(6) without time zone NOT NULL,
    order_number character varying(50) NOT NULL,
    payment_status character varying(255),
    shipping_address text,
    shipping_cost numeric(10,2),
    status character varying(255) NOT NULL,
    total_amount numeric(10,2),
    total_amount_ht numeric(10,2),
    total_vat_amount numeric(10,2),
    updated_at timestamp(6) without time zone,
    client_id bigint NOT NULL,
    user_id bigint NOT NULL,
    CONSTRAINT orders_payment_status_check CHECK (((payment_status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'PARTIAL'::character varying, 'OVERDUE'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT orders_status_check CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'CONFIRMED'::character varying, 'PROCESSING'::character varying, 'SHIPPED'::character varying, 'DELIVERED'::character varying, 'CANCELLED'::character varying, 'RETURNED'::character varying, 'PENDING'::character varying])::text[])))
);


ALTER TABLE public.orders OWNER TO postgres;

--
-- Name: orders_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.orders_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.orders_id_seq OWNER TO postgres;

--
-- Name: orders_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.orders_id_seq OWNED BY public.orders.id;


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.permissions (
    id bigint NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.permissions OWNER TO postgres;

--
-- Name: permissions_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.permissions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.permissions_id_seq OWNER TO postgres;

--
-- Name: permissions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.permissions_id_seq OWNED BY public.permissions.id;


--
-- Name: products; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.products (
    id bigint NOT NULL,
    bar_code character varying(50),
    brand character varying(50),
    category character varying(50) NOT NULL,
    created_at timestamp(6) without time zone,
    description text,
    image_url character varying(200),
    is_active boolean,
    is_featured boolean,
    max_stock integer,
    min_stock integer,
    name character varying(100) NOT NULL,
    notes text,
    purchase_price numeric(10,2),
    reference character varying(50) NOT NULL,
    stock integer NOT NULL,
    unit character varying(50),
    unit_price numeric(10,2) NOT NULL,
    updated_at timestamp(6) without time zone,
    vat_rate numeric(5,2),
    weight numeric(8,3),
    CONSTRAINT products_max_stock_check CHECK ((max_stock >= 0)),
    CONSTRAINT products_min_stock_check CHECK ((min_stock >= 0)),
    CONSTRAINT products_stock_check CHECK ((stock >= 0))
);


ALTER TABLE public.products OWNER TO postgres;

--
-- Name: products_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.products_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.products_id_seq OWNER TO postgres;

--
-- Name: products_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.products_id_seq OWNED BY public.products.id;


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.role_permissions (
    role_id bigint NOT NULL,
    permission_id bigint NOT NULL
);


ALTER TABLE public.role_permissions OWNER TO postgres;

--
-- Name: roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.roles (
    id bigint NOT NULL,
    description character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.roles OWNER TO postgres;

--
-- Name: roles_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.roles_id_seq OWNER TO postgres;

--
-- Name: roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.roles_id_seq OWNED BY public.roles.id;


--
-- Name: user_roles; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.user_roles (
    user_id bigint NOT NULL,
    role_id bigint NOT NULL
);


ALTER TABLE public.user_roles OWNER TO postgres;

--
-- Name: users; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.users (
    id bigint NOT NULL,
    account_non_expired boolean NOT NULL,
    account_non_locked boolean NOT NULL,
    created_at timestamp(6) without time zone,
    credentials_non_expired boolean NOT NULL,
    email character varying(255) NOT NULL,
    enabled boolean NOT NULL,
    first_name character varying(255),
    last_login timestamp(6) without time zone,
    last_name character varying(255),
    password character varying(255) NOT NULL,
    personal_target numeric(38,2) NOT NULL,
    updated_at timestamp(6) without time zone,
    username character varying(50) NOT NULL
);


ALTER TABLE public.users OWNER TO postgres;

--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.users_id_seq OWNER TO postgres;

--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: app_settings id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_settings ALTER COLUMN id SET DEFAULT nextval('public.app_settings_id_seq'::regclass);


--
-- Name: clients id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.clients ALTER COLUMN id SET DEFAULT nextval('public.clients_id_seq'::regclass);


--
-- Name: external_payments id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.external_payments ALTER COLUMN id SET DEFAULT nextval('public.external_payments_id_seq'::regclass);


--
-- Name: invoice_items id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoice_items ALTER COLUMN id SET DEFAULT nextval('public.invoice_items_id_seq'::regclass);


--
-- Name: invoices id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoices ALTER COLUMN id SET DEFAULT nextval('public.invoices_id_seq'::regclass);


--
-- Name: order_items id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.order_items ALTER COLUMN id SET DEFAULT nextval('public.order_items_id_seq'::regclass);


--
-- Name: orders id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders ALTER COLUMN id SET DEFAULT nextval('public.orders_id_seq'::regclass);


--
-- Name: permissions id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permissions ALTER COLUMN id SET DEFAULT nextval('public.permissions_id_seq'::regclass);


--
-- Name: products id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products ALTER COLUMN id SET DEFAULT nextval('public.products_id_seq'::regclass);


--
-- Name: roles id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles ALTER COLUMN id SET DEFAULT nextval('public.roles_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Data for Name: app_settings; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.app_settings (id, category, created_at, description, is_encrypted, is_system, setting_key, sort_order, updated_at, setting_value, value_type) FROM stdin;
\.


--
-- Data for Name: clients; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.clients (id, address, city, client_type, company_name, country, created_at, email, follow_up_date, mobile_number, name, notes, phone_number, postal_code, siret_number, status, updated_at, vat_number, assigned_user_id) FROM stdin;
1	123 Avenue des Champs	Paris	COMPANY	TechCorp Solutions	France	2025-08-04 22:34:12.12986	contact@techcorp.fr	\N	\N	TechCorp Solutions	Client entreprise créé automatiquement	01.23.45.67.89	75001	SIRET123456789	ACTIVE	2025-08-04 22:34:12.12986	\N	4
2	45 Rue de la Tech	Lyon	COMPANY	Digital Innovation	France	2025-08-04 22:34:12.135898	info@digital-innov.fr	\N	\N	Digital Innovation	Client entreprise créé automatiquement	02.34.56.78.90	69001	SIRET234567890	ACTIVE	2025-08-04 22:34:12.135898	\N	5
3	78 Boulevard Écolo	Marseille	COMPANY	Green Energy SA	France	2025-08-04 22:34:12.140857	hello@green-energy.fr	\N	\N	Green Energy SA	Client entreprise créé automatiquement	03.45.67.89.01	13001	SIRET345678901	ACTIVE	2025-08-04 22:34:12.140857	\N	6
4	12 Rue Innovation	Toulouse	COMPANY	Startup Factory	France	2025-08-04 22:34:12.145864	team@startup-factory.fr	\N	\N	Startup Factory	Client entreprise créé automatiquement	04.56.78.90.12	31000	SIRET456789012	ACTIVE	2025-08-04 22:34:12.145864	\N	7
5	89 Avenue Commerce	Bordeaux	COMPANY	Global Trade Ltd	France	2025-08-04 22:34:12.150855	sales@global-trade.fr	\N	\N	Global Trade Ltd	Client entreprise créé automatiquement	05.67.89.01.23	33000	SIRET567890123	ACTIVE	2025-08-04 22:34:12.150855	\N	8
6	34 Place Technologie	Lille	COMPANY	Smart Solutions	France	2025-08-04 22:34:12.155857	contact@smart-sol.fr	\N	\N	Smart Solutions	Client entreprise créé automatiquement	01.78.90.12.34	59000	SIRET678901234	ACTIVE	2025-08-04 22:34:12.155857	\N	9
7	56 Rue Futur	Nantes	COMPANY	Future Corp	France	2025-08-04 22:34:12.159862	info@future-corp.fr	\N	\N	Future Corp	Client entreprise créé automatiquement	02.89.01.23.45	44000	SIRET789012345	ACTIVE	2025-08-04 22:34:12.159862	\N	10
8	67 Boulevard Créatif	Strasbourg	COMPANY	Innovation Hub	France	2025-08-04 22:34:12.164305	hello@innov-hub.fr	\N	\N	Innovation Hub	Client entreprise créé automatiquement	03.90.12.34.56	67000	SIRET890123456	ACTIVE	2025-08-04 22:34:12.164305	\N	11
9	12 Rue de la Paix	Paris	INDIVIDUAL	\N	France	2025-08-04 22:34:12.167265	jean.dupont@email.fr	\N	\N	Jean Dupont	Client particulier créé automatiquement	06.12.34.56.78	75002	\N	ACTIVE	2025-08-04 22:34:12.167265	\N	4
10	23 Avenue Victor Hugo	Lyon	INDIVIDUAL	\N	France	2025-08-04 22:34:12.17127	marie.durand@email.fr	\N	\N	Marie Durand	Client particulier créé automatiquement	06.23.45.67.89	69002	\N	ACTIVE	2025-08-04 22:34:12.17127	\N	5
11	34 Boulevard Saint-Michel	Marseille	INDIVIDUAL	\N	France	2025-08-04 22:34:12.176277	pierre.martin@email.fr	\N	\N	Pierre Martin	Client particulier créé automatiquement	06.34.56.78.90	13002	\N	ACTIVE	2025-08-04 22:34:12.176277	\N	6
12	45 Rue Nationale	Toulouse	INDIVIDUAL	\N	France	2025-08-04 22:34:12.181279	sophie.bernard@email.fr	\N	\N	Sophie Bernard	Client particulier créé automatiquement	06.45.67.89.01	31001	\N	ACTIVE	2025-08-04 22:34:12.181279	\N	7
13	56 Place Gambetta	Bordeaux	INDIVIDUAL	\N	France	2025-08-04 22:34:12.186272	antoine.moreau@email.fr	\N	\N	Antoine Moreau	Client particulier créé automatiquement	06.56.78.90.12	33001	\N	ACTIVE	2025-08-04 22:34:12.186272	\N	8
14	67 Rue de la République	Lille	INDIVIDUAL	\N	France	2025-08-04 22:34:12.190272	julie.rousseau@email.fr	\N	\N	Julie Rousseau	Client particulier créé automatiquement	06.67.89.01.23	59001	\N	ACTIVE	2025-08-04 22:34:12.190272	\N	9
15	\N	\N	COMPANY	\N	\N	2025-08-04 22:34:12.193438	prospect1@email.fr	2025-08-05 22:34:12.1924	\N	Prospect 1	\N	06.00.00.00.00	\N	\N	PROSPECT	2025-08-04 22:34:12.193438	\N	4
16	\N	\N	COMPANY	\N	\N	2025-08-04 22:34:12.197402	prospect2@email.fr	2025-08-06 22:34:12.196406	\N	Prospect 2	\N	06.00.00.00.01	\N	\N	PROSPECT	2025-08-04 22:34:12.197402	\N	5
17	\N	\N	COMPANY	\N	\N	2025-08-04 22:34:12.201413	prospect3@email.fr	2025-08-07 22:34:12.20041	\N	Prospect 3	\N	06.00.00.00.02	\N	\N	PROSPECT	2025-08-04 22:34:12.201413	\N	6
18	Rue du Béguinage 11	Bruxelles	INDIVIDUAL		Belgique	2025-08-05 01:05:47.943606	mackaldine@gmail.com	\N	\N	Diallo Amadou	c'est un client hors Belgique	+32467613461	1000		ACTIVE	2025-08-05 01:05:47.943606	\N	4
\.


--
-- Data for Name: external_payments; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.external_payments (id, amount, card_brand, card_last_four, card_type, client_ip, completed_at, created_at, customer_email, customer_name, expires_at, failure_reason, gateway_fee, gateway_payment_intent_id, gateway_provider, gateway_session_id, gateway_transaction_id, net_amount, payment_method, security_token, status, updated_at, user_agent, invoice_id) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
\.


--
-- Data for Name: invoice_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.invoice_items (id, description, discount_amount, discount_rate, quantity, reference, total_price, total_price_ht, total_vat_amount, unit, unit_price, vat_rate, invoice_id) FROM stdin;
\.


--
-- Data for Name: invoices; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.invoices (id, billing_address, created_at, discount_amount, discount_rate, due_date, email_sent, email_sent_date, invoice_date, invoice_number, invoice_type, notes, paid_amount, payment_date, payment_method, payment_reference, pdf_path, shipping_cost, status, terms_conditions, total_amount, total_amount_ht, total_vat_amount, updated_at, order_id) FROM stdin;
\.


--
-- Data for Name: order_items; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.order_items (id, discount_amount, discount_rate, quantity, total_price, total_price_ht, total_vat_amount, unit_price, vat_rate, order_id, product_id, description) FROM stdin;
\.


--
-- Data for Name: orders; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.orders (id, billing_address, created_at, delivery_date, discount_amount, discount_rate, expected_delivery_date, internal_notes, notes, order_date, order_number, payment_status, shipping_address, shipping_cost, status, total_amount, total_amount_ht, total_vat_amount, updated_at, client_id, user_id) FROM stdin;
\.


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.permissions (id, description, name) FROM stdin;
1	Permission READ	READ
2	Permission WRITE	WRITE
3	Permission DELETE	DELETE
4	Permission MANAGE	MANAGE
\.


--
-- Data for Name: products; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.products (id, bar_code, brand, category, created_at, description, image_url, is_active, is_featured, max_stock, min_stock, name, notes, purchase_price, reference, stock, unit, unit_price, updated_at, vat_rate, weight) FROM stdin;
1	BCCRM-001	Microsoft	Logiciels	2025-08-04 22:34:12.212408	Solution CRM complète pour entreprises	\N	t	f	\N	10	Logiciel CRM Pro	\N	199.99	CRM-001	50	licence	299.99	2025-08-04 22:34:12.212408	18.00	0.000
2	BCOFF-001	Microsoft	Logiciels	2025-08-04 22:34:12.218397	Suite bureautique professionnelle	\N	t	f	\N	20	Suite Office Business	\N	89.99	OFF-001	100	licence	149.99	2025-08-04 22:34:12.218397	18.00	0.000
3	BCAV-001	Microsoft	Logiciels	2025-08-04 22:34:12.221396	Protection antivirus pour entreprises	\N	t	f	\N	15	Antivirus Enterprise	\N	49.99	AV-001	75	licence	89.99	2025-08-04 22:34:12.221396	18.00	0.000
4	BCCOMPTA-001	Microsoft	Logiciels	2025-08-04 22:34:12.224396	Gestion comptable simplifiée	\N	t	f	\N	6	Logiciel Comptabilité	\N	129.99	COMPTA-001	30	licence	199.99	2025-08-04 22:34:12.224396	18.00	0.000
5	BCLAPTOP-001	TechBrand	Matériel	2025-08-04 22:34:12.227397	Laptop professionnel haute performance	\N	t	f	\N	5	Ordinateur Portable Pro	\N	699.99	LAPTOP-001	25	pièce	899.99	2025-08-04 22:34:12.227397	18.00	0.000
6	BCSCREEN-001	TechBrand	Matériel	2025-08-04 22:34:12.230431	Moniteur professionnel 4K	\N	t	f	\N	8	Écran 27 pouces 4K	\N	249.99	SCREEN-001	40	pièce	349.99	2025-08-04 22:34:12.230431	18.00	0.000
7	BCKEYB-001	TechBrand	Matériel	2025-08-04 22:34:12.232395	Clavier gaming professionnel	\N	t	f	\N	12	Clavier Mécanique RGB	\N	79.99	KEYB-001	60	pièce	129.99	2025-08-04 22:34:12.232395	18.00	0.000
8	BCMOUSE-001	TechBrand	Matériel	2025-08-04 22:34:12.234599	Souris sans fil ergonomique	\N	t	f	\N	16	Souris Ergonomique	\N	29.99	MOUSE-001	80	pièce	49.99	2025-08-04 22:34:12.234599	18.00	0.000
9	BCCAM-001	TechBrand	Matériel	2025-08-04 22:34:12.237411	Caméra HD pour visioconférence	\N	t	f	\N	9	Webcam HD Pro	\N	49.99	CAM-001	45	pièce	79.99	2025-08-04 22:34:12.237411	18.00	0.000
10	BCFORM-001	TechServices	Services	2025-08-04 22:34:12.239404	Formation complète Office 365	\N	t	f	\N	199	Formation Office 365	\N	299.99	FORM-001	999	heure	499.99	2025-08-04 22:34:12.239404	18.00	0.000
11	BCSUPP-001	TechServices	Services	2025-08-04 22:34:12.241701	Support technique premium	\N	t	f	\N	199	Support Technique	\N	59.99	SUPP-001	999	heure	99.99	2025-08-04 22:34:12.241701	18.00	0.000
12	BCCONS-001	TechServices	Services	2025-08-04 22:34:12.243917	Consultation informatique	\N	t	f	\N	199	Consultation IT	\N	100.00	CONS-001	999	heure	150.00	2025-08-04 22:34:12.243917	18.00	0.000
13	BCINSTALL-001	TechServices	Services	2025-08-04 22:34:12.24632	Installation réseau entreprise	\N	t	f	\N	199	Installation Réseau	\N	199.99	INSTALL-001	999	heure	299.99	2025-08-04 22:34:12.24632	18.00	0.000
\.


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.role_permissions (role_id, permission_id) FROM stdin;
1	2
1	3
1	4
1	1
2	4
2	2
2	1
3	2
3	1
\.


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.roles (id, description, name) FROM stdin;
1	Rôle ADMIN	ADMIN
2	Rôle MANAGER	MANAGER
3	Rôle USER	USER
\.


--
-- Data for Name: user_roles; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.user_roles (user_id, role_id) FROM stdin;
1	1
2	2
3	2
4	3
5	3
6	3
7	3
8	3
9	3
10	3
11	3
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: postgres
--

COPY public.users (id, account_non_expired, account_non_locked, created_at, credentials_non_expired, email, enabled, first_name, last_login, last_name, password, personal_target, updated_at, username) FROM stdin;
3	t	t	2025-08-04 22:34:09.617667	t	manager2@commercial.com	t	Pierre	\N	Martin	$2a$12$rzpjOtbKaSvDekE17IoT5uP6GayHwvSozxB351Fq8NXFbzY.GDr8C	280000.00	2025-08-04 22:34:09.617667	manager2
4	t	t	2025-08-04 22:34:10.838897	t	marie@commercial.com	t	Marie	\N	Lemoine	$2a$12$caCeOrH2fNrNcx0MQiSYiOZ88nKDmwojTO.5EZtnaFL/ZyeNXA6QO	120000.00	2025-08-04 22:34:10.838897	marie.commercial
5	t	t	2025-08-04 22:34:10.8464	t	paul@commercial.com	t	Paul	\N	Durand	$2a$12$hkTIuMKRbwC//FHcrwspMuyDTor0rvIYkPurgF0Z7wsM18rzgzFcy	110000.00	2025-08-04 22:34:10.8464	paul.commercial
6	t	t	2025-08-04 22:34:10.85041	t	julie@commercial.com	t	Julie	\N	Bernard	$2a$12$Q7GzG8vYq8DIXOdMG9M2S.Mo6WHmyI5nbu9NE42wLxbJ/ytl8TrG2	100000.00	2025-08-04 22:34:10.85041	julie.commercial
7	t	t	2025-08-04 22:34:10.854282	t	antoine@commercial.com	t	Antoine	\N	Rousseau	$2a$12$kVhondTgMQGQOXiGxJyzdeHyh6dUIrJycfvdeBepq1nS7zJ8MnSky	90000.00	2025-08-04 22:34:10.854282	antoine.commercial
9	t	t	2025-08-04 22:34:12.107883	t	emma@commercial.com	t	Emma	\N	Lefebvre	$2a$12$TiHN/wwOgr7o56agz24PJewFYbwNWVxis14GicQRdU5J.LenXPlrK	105000.00	2025-08-04 22:34:12.107883	emma.commercial
10	t	t	2025-08-04 22:34:12.113869	t	thomas@commercial.com	t	Thomas	\N	Roux	$2a$12$QnWPOjMMaozHzgslgAVjy.hqDe0.OGdQjZfF/yb8uf9qJkqZWirvO	95000.00	2025-08-04 22:34:12.113869	thomas.commercial
11	t	t	2025-08-04 22:34:12.118894	t	clara@commercial.com	t	Clara	\N	Fournier	$2a$12$IJnfIRh1MSFI8NywkINUIOTeQ5mIwVbXyuEgJGezAJGHB/QNg4bqa	85000.00	2025-08-04 22:34:12.118894	clara.commercial
8	t	t	2025-08-04 22:34:12.099264	t	lucas@commercial.com	f	Lucas	\N	Moreau	$2a$12$0Zzs1k6sQOg5KYXgN79JEOhkTAFlr8WZ1Rva2GxB6vJN30Pmjn/gG	115000.00	2025-08-07 23:14:05.410759	lucas.commercial
2	t	t	2025-08-04 22:34:08.97601	t	manager1@commercial.com	t	Sophie	2025-08-17 17:58:13.699653	Dubois	$2a$12$FhBhTBvJKD.qq4wIMFbUNeCqHozFOzO5HRWqYzj7//9kagwFEgrNS	300000.00	2025-08-17 17:58:13.699653	manager1
1	t	t	2025-08-04 22:34:08.347499	t	admin@commercial.com	t	Jean	2025-08-22 18:45:59.491053	Administrateur	$2a$12$aV9lU2nA0EORj5KzhuZ4iOHJmIWECi6VQI3DIe/ewuYz3KNwJ0k3u	0.00	2025-08-22 18:45:59.492074	admin
\.


--
-- Name: app_settings_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.app_settings_id_seq', 1, false);


--
-- Name: clients_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.clients_id_seq', 18, true);


--
-- Name: external_payments_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.external_payments_id_seq', 1, false);


--
-- Name: invoice_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.invoice_items_id_seq', 1, false);


--
-- Name: invoices_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.invoices_id_seq', 1, false);


--
-- Name: order_items_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.order_items_id_seq', 1, false);


--
-- Name: orders_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.orders_id_seq', 1, false);


--
-- Name: permissions_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.permissions_id_seq', 4, true);


--
-- Name: products_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.products_id_seq', 13, true);


--
-- Name: roles_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.roles_id_seq', 3, true);


--
-- Name: users_id_seq; Type: SEQUENCE SET; Schema: public; Owner: postgres
--

SELECT pg_catalog.setval('public.users_id_seq', 11, true);


--
-- Name: app_settings app_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT app_settings_pkey PRIMARY KEY (id);


--
-- Name: clients clients_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT clients_pkey PRIMARY KEY (id);


--
-- Name: external_payments external_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.external_payments
    ADD CONSTRAINT external_payments_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: app_settings idx_settings_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT idx_settings_key UNIQUE (setting_key);


--
-- Name: invoice_items invoice_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT invoice_items_pkey PRIMARY KEY (id);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);


--
-- Name: order_items order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT order_items_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (id);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);


--
-- Name: users uk_6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: app_settings uk_7p82g7l6uve2vd8l30djhxpel; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT uk_7p82g7l6uve2vd8l30djhxpel UNIQUE (setting_key);


--
-- Name: invoices uk_e718q5klx5pempy28p2nx88a6; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT uk_e718q5klx5pempy28p2nx88a6 UNIQUE (order_id);


--
-- Name: products uk_klkck760tghhxldwjx22usej5; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT uk_klkck760tghhxldwjx22usej5 UNIQUE (reference);


--
-- Name: invoices uk_l1x55mfsay7co0r3m9ynvipd5; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT uk_l1x55mfsay7co0r3m9ynvipd5 UNIQUE (invoice_number);


--
-- Name: orders uk_nthkiu7pgmnqnu86i2jyoe2v7; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT uk_nthkiu7pgmnqnu86i2jyoe2v7 UNIQUE (order_number);


--
-- Name: roles uk_ofx66keruapi6vyqpv6f2or37; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT uk_ofx66keruapi6vyqpv6f2or37 UNIQUE (name);


--
-- Name: permissions uk_pnvtwliis6p05pn6i3ndjrqt2; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT uk_pnvtwliis6p05pn6i3ndjrqt2 UNIQUE (name);


--
-- Name: users uk_r43af9ap4edm43mmtq01oddj6; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_r43af9ap4edm43mmtq01oddj6 UNIQUE (username);


--
-- Name: clients uk_srv16ica2c1csub334bxjjb59; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT uk_srv16ica2c1csub334bxjjb59 UNIQUE (email);


--
-- Name: user_roles user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_settings_category; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_settings_category ON public.app_settings USING btree (category);


--
-- Name: idx_settings_updated; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_settings_updated ON public.app_settings USING btree (updated_at);


--
-- Name: orders fk32ql8ubntj5uh44ph9659tiih; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fk32ql8ubntj5uh44ph9659tiih FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: external_payments fk45gf4nscakm87sji2ywbpc2d1; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.external_payments
    ADD CONSTRAINT fk45gf4nscakm87sji2ywbpc2d1 FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);


--
-- Name: invoice_items fk46ae0lhu1oqs7cv91fn6y9n7w; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoice_items
    ADD CONSTRAINT fk46ae0lhu1oqs7cv91fn6y9n7w FOREIGN KEY (invoice_id) REFERENCES public.invoices(id);


--
-- Name: invoices fk4ko3y00tkkk2ya3p6wnefjj2f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT fk4ko3y00tkkk2ya3p6wnefjj2f FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: order_items fkbioxgbv59vetrxe0ejfubep1w; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkbioxgbv59vetrxe0ejfubep1w FOREIGN KEY (order_id) REFERENCES public.orders(id);


--
-- Name: role_permissions fkegdk29eiy7mdtefy5c7eirr6e; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkegdk29eiy7mdtefy5c7eirr6e FOREIGN KEY (permission_id) REFERENCES public.permissions(id);


--
-- Name: user_roles fkh8ciramu9cc9q3qcqiv4ue8a6; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkh8ciramu9cc9q3qcqiv4ue8a6 FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: user_roles fkhfh9dx7w3ubf1co1vdev94g3f; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: clients fkk8btot3pvvc9qcfaabqm1neoi; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.clients
    ADD CONSTRAINT fkk8btot3pvvc9qcfaabqm1neoi FOREIGN KEY (assigned_user_id) REFERENCES public.users(id);


--
-- Name: orders fkm2dep9derpoaehshbkkatam3v; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT fkm2dep9derpoaehshbkkatam3v FOREIGN KEY (client_id) REFERENCES public.clients(id);


--
-- Name: role_permissions fkn5fotdgk8d1xvo8nav9uv3muc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT fkn5fotdgk8d1xvo8nav9uv3muc FOREIGN KEY (role_id) REFERENCES public.roles(id);


--
-- Name: order_items fkocimc7dtr037rh4ls4l95nlfi; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.order_items
    ADD CONSTRAINT fkocimc7dtr037rh4ls4l95nlfi FOREIGN KEY (product_id) REFERENCES public.products(id);


--
-- PostgreSQL database dump complete
--

