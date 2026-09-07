(ns vehicle.app-test
  (:require [cljs.test :refer [deftest is testing use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]
            [vehicle.app :as app]))

(use-fixtures :each
  {:before (fn [] (rf/clear-subscription-cache!) (reset! rf-db/app-db {}))})

(deftest initialize-db-sets-defaults
  (testing ":initialize-db populates the ported +page.svelte `app` object"
    (rf/dispatch-sync [:initialize-db])
    (is (= app/default-db @rf-db/app-db))
    (is (= "Ai etzhayyim Project Vehicle" @(rf/subscribe [:title])))
    (is (= "etzhayyim-project-vehicle" @(rf/subscribe [:project])))
    (is (= "etzhayyim-project-vehicle" @(rf/subscribe [:name])))
    (is (= "cloudflare surface" @(rf/subscribe [:kind])))
    (is (= 1 @(rf/subscribe [:route-count])))
    (is (= ["v3h1cl01.etzhayyim.com/*"]
           @(rf/subscribe [:routes])))
    (is (= 13 (count @(rf/subscribe [:vars]))))
    (is (true? @(rf/subscribe [:xrpc?])))
    (is (= "cljs/src/vehicle/app.cljs"
           @(rf/subscribe [:relative-path])))))

(deftest vars-sub-carries-every-original-binding-name
  (testing "no runtime var name was dropped or renamed during the port"
    (rf/dispatch-sync [:initialize-db])
    (is (= #{"AGENTGATEWAY_MCP_ROUTER_URL" "APP_CAPABILITIES" "APP_DEPLOY_AT"
             "APP_DEPLOY_SHA" "APP_DESCRIPTION" "APP_DISPLAY_NAME"
             "APP_FRAMEWORK" "APP_NANOID" "APP_PERFORMER_TYPE" "APP_SOURCE"
             "APP_TEMPLATE" "APP_UI_TYPE" "APP_VERSION"}
           (set @(rf/subscribe [:vars]))))))

(deftest routes-sub-reflects-db-not-a-fixed-value
  (testing ":routes subscription reads whatever is in the db"
    (reset! rf-db/app-db {:routes ["only-one.example/*"]})
    (is (= ["only-one.example/*"] @(rf/subscribe [:routes])))))

(deftest xrpc-sub-reflects-db-not-a-fixed-value
  (testing ":xrpc? subscription reads whatever is in the db"
    (reset! rf-db/app-db {:xrpc? false})
    (is (false? @(rf/subscribe [:xrpc?])))))

(deftest initialize-db-overwrites-prior-state
  (testing ":initialize-db resets to defaults even if the db already had other data"
    (reset! rf-db/app-db {:title "stale" :unrelated 42})
    (rf/dispatch-sync [:initialize-db])
    (is (= app/default-db @rf-db/app-db))))
