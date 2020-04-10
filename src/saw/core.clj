(ns saw.core
  (:require
   [clojure.java.io :as io]
   [saw.provider :as provider]
   [saw.session :as session]
   [saw.config :as config]
   [saw.util :refer [error-as-value] :as u]))

(defn session []
  (session/find))

(defn validate-session
  ([region]
   (when-let [s (session)]
     (->> (provider/resolve s)
          (validate-session region))))
  ([region creds]
   (session/validate! region creds)))

(defn clear-session []
  (session/clear!))

(defn- lookup-profile [profile]
  (-> (config/read-credentials-file)
      (get profile)))

(defn- gen-session-name []
  (str (System/currentTimeMillis)))

(defn login
  ([provider]
   (if (provider/creds? provider)
     provider
     (-> (provider/resolve provider)
         (provider/set!))))
  ([provider mfa-code]
   (if-let [role (System/getenv "AWS_MFA_ARN")]
     (login provider mfa-code role)
     {:error "AWS_MFA_ARN env not set"}))
  ([{:keys [region] :as provider} mfa-code mfa-role]
   (let [sp  (->> (provider/resolve provider)
                  (session/create region mfa-code mfa-role))]
     (session/cache! sp)
     (login sp))))

(defn assume
  ([profile]
   (let [{:keys [role_arn region]} (lookup-profile profile)
         creds  (-> (session/find)
                    (provider/resolve))]
     (assume creds role_arn region)))
  ([creds profile]
   (let [{:keys [role_arn region]} (lookup-profile profile)]
     (assume creds role_arn region)))
  ([creds role-arn region]
   (let [session-name (gen-session-name)
         sp (session/assume-role region role-arn session-name creds)]
     (login sp))))

(defn creds
  ([] (provider/lookup))
  ([provider] (provider/resolve provider)))

(defn static-creds [creds]
  (session/as-static-creds creds))
