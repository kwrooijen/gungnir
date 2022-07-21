# Transactions

The `gungnir.transaction` namespace is responsible for managing the database
transactions. There are two ways to write transactions in Gungnir.

## Simple transactions

Simple transactions can be executes with the `gungnir.transaction/execute!`
function. This takes a single function containing all of your queries. If at any
point one of these queries fails, none the of queries will be saved to the
database. Optionally you can add a datasource as a second argument. By default
it will use `gungnir.database/*daatsource*`. If a transaction fails with an
exception it won't be caught, so you'll have to catch it yourself.

```clojure
(gungnir.transaction/execute!
 (fn []
   (q/save! (changeset/create {:account/name "foo"}))
   (q/save! (changeset/create {:account/name "bar"}))))

(gungnir.transaction/execute!
 (fn [] ,,,)
 my-datasource)
```

## Pipeline transactions

Alternatively you can split up your transactions into multiple steps, creating a
pipeline. This can be useful if your transaction is more complex and you'd like
to have bite-sized steps. Another useful addition is that Gungnir will catch any
exceptions in your pipeline and return an error result. You'll be able to know
where in the pipeline the error occurred.

### Defining a pipeline

Transaction pipelines are defined as vectors. Each index of the vector contains
another vector with a key and a function that takes 1 argument (`state`). Each
function is executed and can return a new `state`, which is passed along to the
next function.

Here is an example where we transfer money from one account to the next. It's
clear what these steps do and any one of them can fail.

```clojure
(defn pipeline--transfer-money [sender-id recipient-id amount]
  [[:retrieve-accounts (retrieve-accounts sender-id recipient-id)]
   [:verify-balance (verify-balance amount)]
   [:subtract-from-sender (subtract-from-sender amount)]
   [:add-to-recipient (add-to-recipient amount)]])
```

The first step retrieves the two accounts and adds them to the state. This
allows the next pipes to make use of the queried accounts. If one of the
accounts doesn't exist, the pipe will return the error `:account-not-found`
instead.

```clojure
(defn retrieve-accounts [sender-id recipient-id]
  (fn [_state]
    (let [sender (q/find-by! :account/id sender-id)
          recipient (q/find-by! :account/id recipient-id)
          ids #{(:account/id sender) (:account/id recipient)}]
      (if (and sender recipient)
        {:sender sender
         :recipient recipient}
        (transaction/error
         {:account-not-found (remove ids [sender-id recipient-id])})))))
```

Next we verify the sender's balance. If the sender has enough we simply return
the state, otherwise return the error `:balance-too-low`.

```clojure
(defn verify-balance [amount]
  (fn [{:keys [sender] :as state}]
    (if (>= (:account/balance sender) amount)
      state
      (transaction/error {:balance-too-low sender}))))
```

Lastly we subtract the amount from the sender's balance and add the amount to
the recipients balance. We use the `transaction/changeset->error` function to
create a transactions error if the changeset contains an error. Otherwise we
return the state.


```clojure
(defn subtract-from-sender [amount]
  (fn [{:keys [sender] :as state}]
    (-> (changeset/update sender :account/balance #(- % amount))
        (q/save!)
        (transaction/changeset->error)
        (or state))))

(defn add-to-recipient [amount]
  (fn [{:keys [recipient] :as state}]
    (-> (changeset/update recipient :account/balance #(+ % amount))
        (q/save!)
        (transaction/changeset->error)
        (or state))))
```

With all this setup we can execute the pipeline using
`gungnir.transaction/execute-pipeline!`.

```clojure
(let [id1 (-> {:account/balance 100} changeset/create q/save! :account/id)
      id2 (-> {:account/balance 100} changeset/create q/save! :account/id)]
  (transaction/execute-pipeline!
   (pipeline--transfer-money id1 id2 20)))
;;=> #:transaction{,,,}
```

If we try to move too much the transaction will return a `:transaction/error`
key, similar to how changesets return their errors.

```clojure
(let [id1 (-> {:account/balance 100} changeset/create q/save! :account/id)
      id2 (-> {:account/balance 100} changeset/create q/save! :account/id)]
  (transaction/execute-pipeline!
   (pipeline--transfer-money id1 id2 20)))
;;=> #:transaction{:error {:balance-too-low ,,,}}
```

---

<div class="footer-navigation">
<span>Previous: <a href="https://kwrooijen.github.io/gungnir/query.html">query</a></span>
<span>Next: <a href="https://kwrooijen.github.io/gungnir/ui.html">UI</a></span>
</div>
